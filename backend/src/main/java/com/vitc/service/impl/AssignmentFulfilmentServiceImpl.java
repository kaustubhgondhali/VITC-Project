package com.vitc.service.impl;

import com.vitc.dto.response.AdminAssignmentFileResponse;
import com.vitc.dto.response.AssignmentDeliveryResponse;
import com.vitc.dto.response.AssignmentDownloadInfoResponse;
import com.vitc.dto.response.AssignmentFileUploadResponse;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.entity.Assignment;
import com.vitc.entity.AssignmentOrder;
import com.vitc.entity.EmailDeliveryLog;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EmailType;
import com.vitc.entity.enums.OrderStatus;
import com.vitc.entity.enums.PaymentOrderStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.AssignmentOrderMapper;
import com.vitc.mapper.EmailDeliveryLogMapper;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.AssignmentRepository;
import com.vitc.repository.EmailDeliveryLogRepository;
import com.vitc.repository.PaymentOrderRepository;
import com.vitc.security.upload.UploadCategory;
import com.vitc.security.upload.UploadFileValidator;
import com.vitc.service.AssignmentFulfilmentService;
import com.vitc.service.AuditLogService;
import com.vitc.service.EmailService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AssignmentFulfilmentServiceImpl implements AssignmentFulfilmentService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 random bytes, URL-safe Base64 without padding. */
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String CONFIRMED_SUBJECT = "Your VITC assignment order is confirmed";
    private static final String DELIVERED_SUBJECT = "Your VITC project is ready to download";
    /** Paid orders still waiting for their project files. */
    private static final List<OrderStatus> WAITING = List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS);

    private final AssignmentOrderRepository orderRepository;
    private final AssignmentRepository assignmentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final EmailDeliveryLogRepository logRepository;
    private final EmailService emailService;
    private final UploadFileValidator uploadValidator;
    private final AuditLogService auditLogService;
    private final String frontendUrl;
    private final String supportEmail;
    private final String deliveryDir;
    private final long downloadLinkDays;
    private final TransactionTemplate requiresNew;

    /** Private storage root - never under the publicly served app.upload.dir. */
    private Path root;

    public AssignmentFulfilmentServiceImpl(AssignmentOrderRepository orderRepository,
                                           AssignmentRepository assignmentRepository,
                                           PaymentOrderRepository paymentOrderRepository,
                                           EmailDeliveryLogRepository logRepository,
                                           EmailService emailService,
                                           UploadFileValidator uploadValidator,
                                           AuditLogService auditLogService,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl,
                                           @Value("${app.mail.support-email:support@vitc.local}") String supportEmail,
                                           @Value("${app.assignment.delivery-dir:private-uploads/assignment-deliveries}") String deliveryDir,
                                           @Value("${app.assignment.download-link-days:30}") long downloadLinkDays) {
        this.orderRepository = orderRepository;
        this.assignmentRepository = assignmentRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.logRepository = logRepository;
        this.emailService = emailService;
        this.uploadValidator = uploadValidator;
        this.auditLogService = auditLogService;
        this.frontendUrl = frontendUrl;
        this.supportEmail = supportEmail;
        this.deliveryDir = deliveryDir;
        this.downloadLinkDays = downloadLinkDays;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    void init() {
        try {
            root = Paths.get(deliveryDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create assignment delivery directory: " + deliveryDir, e);
        }
    }

    /* ============================ Payment ============================ */

    @Override
    @Transactional
    public void onAssignmentPaid(PaymentOrder paymentOrder) {
        if (paymentOrder == null || !"ASSIGNMENT".equals(paymentOrder.getItemType())) {
            return;
        }
        try {
            AssignmentOrder order = ensureAssignmentOrder(paymentOrder);
            if (order == null) {
                log.error("Paid assignment order {} ('{}') matches no assignment in the catalogue - "
                        + "it has to be fulfilled manually", paymentOrder.getOrderCode(), paymentOrder.getItemTitle());
            }
            String code = paymentOrder.getOrderCode();
            if (order != null && order.getDeliveredAt() == null && hasProjectFile(order.getAssignment())) {
                // Ready-made project: deliver it straight away instead of a "we are preparing it" mail.
                String token = attach(order, order.getAssignment().getProjectFilePath(),
                        order.getAssignment().getProjectFileName(), order.getAssignment().getProjectContentType(),
                        order.getAssignment().getProjectSizeBytes(), null);
                orderRepository.save(order);
                sendOnceAfterCommit(EmailType.ASSIGNMENT_DELIVERED + ":" + code + ":paid", EmailType.ASSIGNMENT_DELIVERED,
                        paymentOrder.getEmail(), code, titleOf(order), DELIVERED_SUBJECT, deliveredHtml(order, token));
                log.info("Order {} delivered automatically with the ready-made project file", code);
            } else {
                sendOnceAfterCommit(EmailType.ASSIGNMENT_ORDER_CONFIRMED + ":" + code, EmailType.ASSIGNMENT_ORDER_CONFIRMED,
                        paymentOrder.getEmail(), code, paymentOrder.getItemTitle(), CONFIRMED_SUBJECT,
                        confirmationHtml(paymentOrder.getCustomerName(), paymentOrder.getItemTitle(), code,
                                paymentOrder.getTotalAmount(), deliveryDaysOf(order)));
            }
        } catch (Exception ex) {
            // Fulfilment problems must never break a verified payment.
            log.error("Assignment fulfilment could not be started for paid order {}: {}",
                    paymentOrder.getOrderCode(), ex.getMessage());
        }
    }

    /**
     * The assignment order that shares the checkout order's code, created on first use.
     * Returns null when the purchase cannot be matched to a catalogue assignment.
     */
    private AssignmentOrder ensureAssignmentOrder(PaymentOrder paymentOrder) {
        AssignmentOrder existing = orderRepository.findByOrderCode(paymentOrder.getOrderCode()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == OrderStatus.PENDING) {
                existing.setStatus(OrderStatus.IN_PROGRESS);
                existing = orderRepository.save(existing);
            }
            return existing;
        }
        Assignment assignment = resolveAssignment(paymentOrder);
        if (assignment == null) {
            return null;
        }
        String phone = paymentOrder.getPhone();
        return orderRepository.save(AssignmentOrder.builder()
                .orderCode(paymentOrder.getOrderCode())
                .assignment(assignment)
                .studentName(paymentOrder.getCustomerName())
                .email(paymentOrder.getEmail().trim())
                .phone(phone == null || phone.isBlank() ? "-" : phone)
                .amount(paymentOrder.getTotalAmount())
                .status(OrderStatus.IN_PROGRESS)
                .downloadCount(0)
                .build());
    }

    /** Same matching rule as course purchases: the catalogue id first, then the title or code. */
    private Assignment resolveAssignment(PaymentOrder order) {
        if (order.getItemRefId() != null) {
            Assignment byId = assignmentRepository.findById(order.getItemRefId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        String title = order.getItemTitle();
        if (title == null) {
            return null;
        }
        return assignmentRepository.findAll().stream()
                .filter(a -> title.equalsIgnoreCase(a.getTitle()) || title.equalsIgnoreCase(a.getCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Mails once the payment has committed, in a transaction of its own: a failed email-log insert
     * inside the payment's transaction would otherwise mark it rollback-only. The body is built
     * beforehand, so nothing lazy is touched after the payment's session is gone.
     */
    private void sendOnceAfterCommit(String dedupeKey, EmailType type, String recipient, String orderCode,
                                     String title, String subject, String html) {
        afterCommit(() -> {
            try {
                requiresNew.executeWithoutResult(status ->
                        sendOnce(dedupeKey, type, recipient, orderCode, title, subject, html));
            } catch (Exception ex) {
                log.error("{} email could not be processed for order {}: {}", type, orderCode, ex.getMessage());
            }
        });
    }

    private void sendOnce(String dedupeKey, EmailType type, String recipient, String orderCode,
                          String title, String subject, String html) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        if (logRepository.findByDedupeKey(dedupeKey).isPresent()) {
            log.info("{} email already recorded for {} - skipping duplicate "
                    + "(refresh / repeated confirm / webhook retry)", type, orderCode);
            return;
        }
        EmailDeliveryLog record;
        try {
            record = logRepository.save(newLog(dedupeKey, type, recipient.trim(), orderCode, title));
        } catch (DataIntegrityViolationException raceLoser) {
            // Browser confirm and Razorpay webhook arrived together; the other one sends it.
            log.info("{} email for {} was already claimed by a concurrent request - skipping (idempotent)",
                    type, orderCode);
            return;
        }
        send(record, subject, html);
    }

    /* ============================ Admin: ready-made project files ============================ */

    @Override
    public List<AdminAssignmentFileResponse> listAssignmentFiles() {
        return assignmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Assignment::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::fileResponse)
                .toList();
    }

    @Override
    @Transactional
    public AssignmentFileUploadResponse uploadAssignmentFile(Long assignmentId, MultipartFile file,
                                                             boolean sendToWaitingBuyers) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        UploadFileValidator.ValidatedUpload validated =
                uploadValidator.validate(file, UploadCategory.ASSIGNMENT_DELIVERY);
        store(file, validated.storedName());

        String previous = assignment.getProjectFilePath();
        assignment.setProjectFilePath(validated.storedName());
        assignment.setProjectFileName(validated.originalName());
        assignment.setProjectContentType(validated.contentType());
        assignment.setProjectSizeBytes(validated.sizeBytes());
        assignment.setProjectUploadedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);

        List<String> replaced = new ArrayList<>();
        replaced.add(previous);
        int sent = 0;
        int failed = 0;
        if (sendToWaitingBuyers) {
            for (AssignmentOrder order : orderRepository.findByAssignment_IdAndStatusIn(assignmentId, WAITING)) {
                replaced.add(order.getDeliveryFilePath());
                String token = attach(order, validated.storedName(), validated.originalName(),
                        validated.contentType(), validated.sizeBytes(), null);
                orderRepository.save(order);
                EmailDeliveryLog email = sendDeliveryEmail(order, token);
                sent++;
                if (email.getStatus() != EmailDeliveryStatus.SENT) {
                    failed++;
                }
            }
        }
        cleanUpAfterReplace(validated.storedName(), replaced.toArray(String[]::new));
        auditLogService.log("ASSIGNMENT_PROJECT_FILE_UPLOADED", "Assignment", String.valueOf(assignmentId),
                "Uploaded the project file for " + assignment.getCode()
                        + (sent > 0 ? " and delivered it to " + sent + " waiting order(s)" : ""));
        return new AssignmentFileUploadResponse(fileResponse(assignment), sent, failed);
    }

    @Override
    @Transactional
    public AdminAssignmentFileResponse removeAssignmentFile(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        String previous = assignment.getProjectFilePath();
        assignment.setProjectFilePath(null);
        assignment.setProjectFileName(null);
        assignment.setProjectContentType(null);
        assignment.setProjectSizeBytes(null);
        assignment.setProjectUploadedAt(null);
        assignmentRepository.save(assignment);
        // Buyers who already received this file keep their working download links.
        cleanUpAfterReplace(null, previous);
        auditLogService.log("ASSIGNMENT_PROJECT_FILE_REMOVED", "Assignment", String.valueOf(assignmentId),
                "Removed the project file of " + assignment.getCode());
        return fileResponse(assignment);
    }

    private AdminAssignmentFileResponse fileResponse(Assignment a) {
        return new AdminAssignmentFileResponse(a.getId(), a.getCode(), a.getTitle(), a.getPrice(), a.getActive(),
                a.getProjectFilePath() == null ? null : a.getProjectFileName(),
                a.getProjectFilePath() == null ? null : a.getProjectSizeBytes(),
                a.getProjectFilePath() == null ? null : a.getProjectUploadedAt(),
                orderRepository.countByAssignment_IdAndStatusIn(a.getId(), WAITING),
                orderRepository.countByAssignment_IdAndStatusIn(a.getId(), List.of(OrderStatus.DELIVERED)));
    }

    private boolean hasProjectFile(Assignment assignment) {
        if (assignment == null || assignment.getProjectFilePath() == null) {
            return false;
        }
        Path path = root.resolve(assignment.getProjectFilePath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            log.error("Project file of assignment {} is missing on disk ({})",
                    assignment.getCode(), assignment.getProjectFilePath());
            return false;
        }
        return true;
    }

    /* ============================ Admin ============================ */

    @Override
    @Transactional
    public AssignmentDeliveryResponse deliver(String orderCode, MultipartFile file, String note) {
        AssignmentOrder order = findForAdmin(orderCode);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("This order is cancelled. Set it back to In Progress before delivering.");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw new BadRequestException("The note can be at most 1000 characters.");
        }
        UploadFileValidator.ValidatedUpload validated =
                uploadValidator.validate(file, UploadCategory.ASSIGNMENT_DELIVERY);
        store(file, validated.storedName());

        String previous = order.getDeliveryFilePath();
        String token = attach(order, validated.storedName(), validated.originalName(), validated.contentType(),
                validated.sizeBytes(), cleanNote);
        orderRepository.save(order);
        cleanUpAfterReplace(validated.storedName(), previous);

        EmailDeliveryLog email = sendDeliveryEmail(order, token);
        auditLogService.log("ASSIGNMENT_DELIVERED", "AssignmentOrder", String.valueOf(order.getId()),
                "Delivered project package for order " + order.getOrderCode());
        return deliveryResponse(order, token, email);
    }

    @Override
    @Transactional
    public AssignmentDeliveryResponse resendDownloadLink(String orderCode) {
        AssignmentOrder order = findForAdmin(orderCode);
        if (order.getDeliveryFilePath() == null) {
            throw new BadRequestException("No project file has been uploaded for this order yet.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Only a delivered order has a download link. Deliver it first.");
        }
        String token = issueDownloadToken(order);
        orderRepository.save(order);

        EmailDeliveryLog email = sendDeliveryEmail(order, token);
        auditLogService.log("ASSIGNMENT_DOWNLOAD_LINK_RESENT", "AssignmentOrder", String.valueOf(order.getId()),
                "Issued a new download link for order " + order.getOrderCode());
        return deliveryResponse(order, token, email);
    }

    @Override
    @Transactional
    public AssignmentOrderResponse updateStatus(String orderCode, OrderStatus status) {
        AssignmentOrder order = findForAdmin(orderCode);
        OrderStatus previous = order.getStatus();
        order.setStatus(status);
        AssignmentOrder saved = orderRepository.save(order);
        auditLogService.log("ASSIGNMENT_ORDER_STATUS_CHANGED", "AssignmentOrder", String.valueOf(saved.getId()),
                "Order " + saved.getOrderCode() + ": " + previous + " -> " + status);
        return AssignmentOrderMapper.toResponse(saved);
    }

    /**
     * An existing assignment order, or - for a paid assignment checkout that predates this flow -
     * one created from that payment so it can still be delivered.
     */
    private AssignmentOrder findForAdmin(String orderCode) {
        String code = orderCode == null ? "" : orderCode.trim();
        AssignmentOrder existing = orderRepository.findByOrderCode(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        PaymentOrder paid = paymentOrderRepository.findByOrderCode(code)
                .filter(p -> "ASSIGNMENT".equals(p.getItemType()) && p.getStatus() == PaymentOrderStatus.PAID)
                .orElseThrow(() -> new ResourceNotFoundException("No paid assignment order found with code " + code));
        AssignmentOrder created = ensureAssignmentOrder(paid);
        if (created == null) {
            throw new BadRequestException("Order " + code + " (\"" + paid.getItemTitle()
                    + "\") does not match any assignment in the catalogue, so it cannot be delivered from here.");
        }
        return created;
    }

    /* ============================ Buyer download ============================ */

    @Override
    public AssignmentDownloadInfoResponse downloadInfo(String token) {
        AssignmentOrder order = findDownloadable(token);
        return new AssignmentDownloadInfoResponse(order.getOrderCode(), titleOf(order), order.getDeliveryFileName(),
                order.getDeliverySizeBytes(), order.getDeliveryNote(), order.getDeliveredAt(),
                order.getDownloadExpiresAt());
    }

    @Override
    @Transactional
    public AssignmentDownload openDownload(String token) {
        AssignmentOrder order = findDownloadable(token);
        Path path = root.resolve(order.getDeliveryFilePath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            log.error("Delivered package for order {} is missing on disk ({})",
                    order.getOrderCode(), order.getDeliveryFilePath());
            throw new ResourceNotFoundException("This project file is no longer available. Please write to "
                    + supportEmail + " with your order ID " + order.getOrderCode() + ".");
        }
        order.setDownloadCount((order.getDownloadCount() == null ? 0 : order.getDownloadCount()) + 1);
        order.setLastDownloadedAt(LocalDateTime.now());
        orderRepository.save(order);

        String contentType = order.getDeliveryContentType() == null || order.getDeliveryContentType().isBlank()
                ? "application/octet-stream" : order.getDeliveryContentType();
        return new AssignmentDownload(new FileSystemResource(path), order.getDeliveryFileName(), contentType,
                path.toFile().length());
    }

    private AssignmentOrder findDownloadable(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw invalidLink();
        }
        AssignmentOrder order = orderRepository.findByDownloadTokenHash(sha256(token)).orElseThrow(this::invalidLink);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getDeliveryFilePath() == null) {
            throw invalidLink();
        }
        if (order.getDownloadExpiresAt() != null && order.getDownloadExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This download link has expired. Write to " + supportEmail
                    + " with your order ID " + order.getOrderCode() + " and we will send you a new one.");
        }
        return order;
    }

    private ResourceNotFoundException invalidLink() {
        return new ResourceNotFoundException("This download link is not valid. Please use the latest link from your email.");
    }

    /* ============================ Email retry ============================ */

    @Override
    @Transactional
    public EmailDeliveryLogResponse retryEmail(EmailDeliveryLog record) {
        AssignmentOrder order = orderRepository.findByOrderCode(record.getOrderCode()).orElse(null);
        if (record.getEmailType() == EmailType.ASSIGNMENT_DELIVERED) {
            if (order == null || order.getDeliveryFilePath() == null || order.getStatus() != OrderStatus.DELIVERED) {
                throw new BadRequestException("This order no longer has a delivered project file to send.");
            }
            // The emailed token is never stored, so a retry issues a new link (the old one stops working).
            String token = issueDownloadToken(order);
            orderRepository.save(order);
            send(record, DELIVERED_SUBJECT, deliveredHtml(order, token));
        } else {
            PaymentOrder paymentOrder = paymentOrderRepository.findByOrderCode(record.getOrderCode()).orElse(null);
            if (paymentOrder != null) {
                send(record, CONFIRMED_SUBJECT, confirmationHtml(paymentOrder.getCustomerName(),
                        paymentOrder.getItemTitle(), paymentOrder.getOrderCode(), paymentOrder.getTotalAmount(),
                        deliveryDaysOf(order)));
            } else if (order != null) {
                send(record, CONFIRMED_SUBJECT, confirmationHtml(order.getStudentName(), titleOf(order),
                        order.getOrderCode(), order.getAmount(), deliveryDaysOf(order)));
            } else {
                throw new ResourceNotFoundException("The order for this email no longer exists");
            }
        }
        return EmailDeliveryLogMapper.toResponse(record);
    }

    /* ============================ Helpers ============================ */

    private EmailDeliveryLog sendDeliveryEmail(AssignmentOrder order, String token) {
        // Every delivery or resend is a deliberate admin action, so each one gets its own log row.
        String dedupeKey = EmailType.ASSIGNMENT_DELIVERED + ":" + order.getOrderCode() + ":" + System.currentTimeMillis();
        EmailDeliveryLog record = logRepository.save(newLog(dedupeKey, EmailType.ASSIGNMENT_DELIVERED,
                order.getEmail().trim(), order.getOrderCode(), titleOf(order)));
        send(record, DELIVERED_SUBJECT, deliveredHtml(order, token));
        return record;
    }

    /** Sends and records the outcome on the log row. Never throws. */
    private void send(EmailDeliveryLog record, String subject, String html) {
        try {
            emailService.sendHtml(record.getRecipientEmail(), subject, html);
            record.setStatus(EmailDeliveryStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            record.setFailureReason(null);
            log.info("{} email sent for order {}", record.getEmailType(), record.getOrderCode());
        } catch (Exception ex) {
            record.setStatus(EmailDeliveryStatus.FAILED);
            record.setFailedAt(LocalDateTime.now());
            record.setFailureReason(trim(ex.getMessage()));
            log.warn("{} email FAILED for order {}: {}",
                    record.getEmailType(), record.getOrderCode(), record.getFailureReason());
        }
        logRepository.save(record);
    }

    private EmailDeliveryLog newLog(String dedupeKey, EmailType type, String recipient, String orderCode, String title) {
        return EmailDeliveryLog.builder()
                .dedupeKey(dedupeKey)
                .emailType(type)
                .recipientEmail(recipient)
                .orderCode(orderCode)
                .courseTitle(title)
                .status(EmailDeliveryStatus.PENDING)
                .build();
    }

    private String confirmationHtml(String name, String title, String orderCode, BigDecimal amount, String deliveryDays) {
        return StudentEmailTemplates.assignmentConfirmed(name, title, orderCode, money(amount), deliveryDays,
                supportEmail);
    }

    private String deliveredHtml(AssignmentOrder order, String token) {
        return StudentEmailTemplates.assignmentDelivered(order.getStudentName(), titleOf(order), order.getOrderCode(),
                order.getDeliveryFileName(), humanSize(order.getDeliverySizeBytes()), order.getDeliveryNote(),
                downloadUrl(token), DAY.format(order.getDownloadExpiresAt()), supportEmail);
    }

    private AssignmentDeliveryResponse deliveryResponse(AssignmentOrder order, String token, EmailDeliveryLog email) {
        return new AssignmentDeliveryResponse(order.getOrderCode(), order.getStatus(),
                AssignmentOrderMapper.toDeliveryInfo(order), downloadUrl(token), order.getDownloadExpiresAt(),
                email.getStatus(), email.getFailureReason());
    }

    /** Points the order at a stored package, marks it DELIVERED and issues a fresh download link. */
    private String attach(AssignmentOrder order, String storedName, String fileName, String contentType,
                          Long sizeBytes, String note) {
        order.setDeliveryFilePath(storedName);
        order.setDeliveryFileName(fileName);
        order.setDeliveryContentType(contentType);
        order.setDeliverySizeBytes(sizeBytes);
        order.setDeliveryNote(note);
        order.setDeliveredAt(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED);
        return issueDownloadToken(order);
    }

    /**
     * Stored packages are shared: an assignment's ready-made file is also the file of every order
     * delivered with it. Once the change commits, each replaced file is deleted only if nothing
     * references it any more; if the change rolls back, the newly stored file is removed instead.
     * Call after the entities are updated, so the reference check sees the new state.
     */
    private void cleanUpAfterReplace(String newlyStored, String... replaced) {
        List<String> unused = new ArrayList<>();
        for (String storedName : replaced) {
            if (storedName != null && !storedName.equals(newlyStored) && !unused.contains(storedName)
                    && !assignmentRepository.existsByProjectFilePath(storedName)
                    && !orderRepository.existsByDeliveryFilePath(storedName)) {
                unused.add(storedName);
            }
        }
        afterCompletion(committed -> {
            if (committed) {
                unused.forEach(this::deleteQuietly);
            } else {
                deleteQuietly(newlyStored);
            }
        });
    }

    /** Replaces any previous link: only the hash of the new token is kept. */
    private String issueDownloadToken(AssignmentOrder order) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        order.setDownloadTokenHash(sha256(token));
        order.setDownloadExpiresAt(LocalDateTime.now().plusDays(downloadLinkDays));
        return token;
    }

    private String downloadUrl(String token) {
        return frontendUrl.replaceAll("/+$", "") + "/assignment-download.html?token=" + token;
    }

    private void store(MultipartFile file, String storedName) {
        Path destination = root.resolve(storedName).normalize();
        if (!destination.startsWith(root)) {
            throw new BadRequestException("Invalid file name");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not store assignment package '{}': {}", storedName, e.getMessage(), e);
            throw new BadRequestException("The file could not be saved. Please try again.");
        }
    }

    private void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Path path = root.resolve(storedName).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not remove assignment package file '{}': {}", storedName, e.getMessage());
        }
    }

    /** Runs once the surrounding transaction has committed - immediately when there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void afterCompletion(Consumer<Boolean> action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String titleOf(AssignmentOrder order) {
        return order.getAssignment() != null ? order.getAssignment().getTitle() : "Your VITC assignment";
    }

    private static String deliveryDaysOf(AssignmentOrder order) {
        return order == null || order.getAssignment() == null ? null : order.getAssignment().getDeliveryDays();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "-" : "Rs. " + amount.toPlainString();
    }

    private static String humanSize(Long bytes) {
        if (bytes == null) {
            return "-";
        }
        if (bytes >= 1024L * 1024) {
            return String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return Math.max(1, bytes / 1024) + " KB";
    }

    private static String trim(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown mail error";
        }
        return message.length() > 380 ? message.substring(0, 380) : message;
    }
}

package com.vitc.service.impl;

import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.dto.response.StudentCredentialsResponse;
import com.vitc.entity.EmailDeliveryLog;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.User;
import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EmailType;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.EmailDeliveryLogMapper;
import com.vitc.repository.EmailDeliveryLogRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.AssignmentFulfilmentService;
import com.vitc.service.EmailService;
import com.vitc.service.StudentCredentialEmailService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StudentCredentialEmailServiceImpl implements StudentCredentialEmailService {

    private static final String PW_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailDeliveryLogRepository logRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String loginUrl;
    private final String supportEmail;
    private final AssignmentFulfilmentService assignmentFulfilmentService;

    public StudentCredentialEmailServiceImpl(EmailDeliveryLogRepository logRepository,
                                             EmailService emailService,
                                             UserRepository userRepository,
                                             PasswordEncoder passwordEncoder,
                                             @Value("${app.student.login-url:http://localhost:5500/student-login.html}") String loginUrl,
                                             @Value("${app.mail.support-email:support@vitc.local}") String supportEmail,
                                             AssignmentFulfilmentService assignmentFulfilmentService) {
        this.logRepository = logRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginUrl = loginUrl;
        this.supportEmail = supportEmail;
        this.assignmentFulfilmentService = assignmentFulfilmentService;
    }

    /* ============================ Trigger ============================ */

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void sendForPaidOrder(PaymentOrder order, StudentCredentialsResponse credentials) {
        try {
            if (order == null || credentials == null || credentials.studentLoginId() == null) {
                return; // not a course purchase / no student account was provisioned
            }
            String recipient = order.getEmail();
            if (recipient == null || recipient.isBlank()) {
                return;
            }
            boolean newAccount = credentials.newAccount() && credentials.temporaryPassword() != null;
            EmailType type = newAccount ? EmailType.STUDENT_CREDENTIALS : EmailType.COURSE_ADDED;
            String dedupeKey = newAccount
                    ? EmailType.STUDENT_CREDENTIALS + ":" + credentials.studentLoginId()
                    : EmailType.COURSE_ADDED + ":" + order.getOrderCode();

            if (logRepository.findByDedupeKey(dedupeKey).isPresent()) {
                log.info("Credential email already recorded for {} - skipping duplicate "
                        + "(refresh / repeated confirm / webhook retry)", dedupeKey);
                return;
            }

            EmailDeliveryLog record;
            try {
                record = logRepository.save(EmailDeliveryLog.builder()
                        .dedupeKey(dedupeKey)
                        .emailType(type)
                        .recipientEmail(recipient.trim())
                        .studentLoginId(credentials.studentLoginId())
                        .studentUserId(userRepository.findByEmailIgnoreCase(recipient.trim())
                                .map(User::getId).orElse(null))
                        .orderCode(order.getOrderCode())
                        .courseTitle(order.getItemTitle())
                        .status(EmailDeliveryStatus.PENDING)
                        .build());
            } catch (DataIntegrityViolationException raceLoser) {
                // Two verification paths (e.g. the browser confirm call and the
                // Razorpay webhook) reached here at almost the same instant.
                // The dedupeKey unique constraint lets only one of them win;
                // the other lands here - this is the idempotency guarantee
                // working as designed, not an error, so no email is sent and
                // nothing is retried.
                log.info("Credential email for {} was already claimed by a concurrent request - "
                        + "skipping (idempotent)", dedupeKey);
                return;
            }

            String html = newAccount
                    ? StudentEmailTemplates.credentials(order.getCustomerName(), credentials.studentLoginId(),
                            credentials.temporaryPassword(), order.getItemTitle(), order.getOrderCode(),
                            money(order), loginUrl, supportEmail)
                    : StudentEmailTemplates.courseAdded(order.getCustomerName(), credentials.studentLoginId(),
                            order.getItemTitle(), order.getOrderCode(), money(order), loginUrl, supportEmail);

            String subject = newAccount
                    ? "Welcome to VITC — Your Student Account Details"
                    : "Your new VITC course is ready";

            deliver(record, subject, html);
        } catch (Exception ex) {
            // A mail problem must never break a verified payment.
            log.error("Credential email could not be processed for order {}: {}",
                    order == null ? "?" : order.getOrderCode(), ex.getMessage());
        }
    }

    /** Sends and records the outcome. Never throws. */
    private void deliver(EmailDeliveryLog record, String subject, String html) {
        try {
            emailService.sendHtml(record.getRecipientEmail(), subject, html);
            record.setStatus(EmailDeliveryStatus.SENT);
            record.setSentAt(LocalDateTime.now());
            record.setFailureReason(null);
            log.info("Credential email sent to student {} (order {})",
                    record.getStudentLoginId(), record.getOrderCode());
        } catch (Exception ex) {
            record.setStatus(EmailDeliveryStatus.FAILED);
            record.setFailedAt(LocalDateTime.now());
            record.setFailureReason(trim(ex.getMessage()));
            log.warn("Credential email FAILED for student {} (order {}): {}",
                    record.getStudentLoginId(), record.getOrderCode(), record.getFailureReason());
        }
        logRepository.save(record);
    }

    /* ============================ Admin ============================ */

    @Override
    public List<EmailDeliveryLogResponse> recentLogs() {
        return logRepository.findTop200ByOrderByIdDesc().stream()
                .map(EmailDeliveryLogMapper::toResponse)
                .toList();
    }

    @Override
    public List<EmailDeliveryLogResponse> logsForStudent(Long studentUserId) {
        return logRepository.findByStudentUserIdOrderByIdDesc(studentUserId).stream()
                .map(EmailDeliveryLogMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EmailDeliveryLogResponse retry(Long logId) {
        EmailDeliveryLog record = logRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Email log not found"));
        if (record.getStatus() == EmailDeliveryStatus.SENT) {
            throw new BadRequestException("This email was already delivered successfully");
        }
        if (!emailService.isConfigured()) {
            throw new BadRequestException("Email is not configured. Set MAIL_HOST, MAIL_USERNAME, "
                    + "MAIL_PASSWORD and MAIL_FROM, then restart the backend.");
        }
        record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
        if (record.getEmailType() == EmailType.ASSIGNMENT_ORDER_CONFIRMED
                || record.getEmailType() == EmailType.ASSIGNMENT_DELIVERED) {
            // Assignment buyers have no student account; their emails are rebuilt from the order.
            return assignmentFulfilmentService.retryEmail(record);
        }

        String subject;
        String html;
        if (record.getEmailType() == EmailType.STUDENT_CREDENTIALS) {
            // The original temporary password is never stored, so a retry issues
            // a fresh one and updates the account.
            User user = resolveUser(record);
            String temporaryPassword = generatePassword();
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            user.setMustChangePassword(true);
            userRepository.save(user);
            record.setStudentUserId(user.getId());
            record.setStudentLoginId(user.getStudentLoginId());
            subject = "Welcome to VITC — Your Student Account Details";
            html = StudentEmailTemplates.credentials(user.getFullName(), user.getStudentLoginId(),
                    temporaryPassword, record.getCourseTitle(), record.getOrderCode(), "-",
                    loginUrl, supportEmail);
        } else {
            User user = resolveUser(record);
            subject = "Your new VITC course is ready";
            html = StudentEmailTemplates.courseAdded(user.getFullName(), user.getStudentLoginId(),
                    record.getCourseTitle(), record.getOrderCode(), "-", loginUrl, supportEmail);
        }
        deliver(record, subject, html);
        return EmailDeliveryLogMapper.toResponse(record);
    }

    @Override
    public void sendTestEmail(String to) {
        if (!emailService.isConfigured()) {
            throw new BadRequestException("Email is not configured. Set MAIL_HOST, MAIL_USERNAME, "
                    + "MAIL_PASSWORD and MAIL_FROM, then restart the backend.");
        }
        emailService.sendHtml(to, "VITC email configuration test", StudentEmailTemplates.test(loginUrl));
    }

    @Override
    public boolean isConfigured() {
        return emailService.isConfigured();
    }

    /* ============================ Helpers ============================ */

    private User resolveUser(EmailDeliveryLog record) {
        User user = record.getStudentUserId() == null
                ? null
                : userRepository.findById(record.getStudentUserId()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(record.getRecipientEmail())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Student account for this email no longer exists"));
        }
        return user;
    }

    private String money(PaymentOrder order) {
        return order.getTotalAmount() == null ? "-" : "Rs. " + order.getTotalAmount().toPlainString();
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder("VITC@");
        for (int i = 0; i < 6; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        return sb.toString();
    }

    private String trim(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown mail error";
        }
        return message.length() > 380 ? message.substring(0, 380) : message;
    }
}

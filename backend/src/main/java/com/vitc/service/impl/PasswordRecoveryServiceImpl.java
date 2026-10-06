package com.vitc.service.impl;

import com.vitc.dto.request.CompletePasswordResetRequest;
import com.vitc.dto.request.PasswordRecoveryRequest;
import com.vitc.dto.request.ResendOtpRequest;
import com.vitc.dto.request.VerifyOtpRequest;
import com.vitc.dto.response.PasswordRecoveryResponse;
import com.vitc.dto.response.VerifyOtpResponse;
import com.vitc.entity.Admin;
import com.vitc.entity.PasswordResetOtp;
import com.vitc.entity.User;
import com.vitc.entity.enums.DeliveryChannel;
import com.vitc.entity.enums.RecoveryPortal;
import com.vitc.entity.enums.UserRole;
import com.vitc.exception.BadRequestException;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.PasswordResetOtpRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.EmailService;
import com.vitc.service.PasswordRecoveryService;
import com.vitc.service.SmsService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetOtpRepository otpRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final String supportEmail;

    public PasswordRecoveryServiceImpl(PasswordResetOtpRepository otpRepository,
                                       AdminRepository adminRepository,
                                       UserRepository userRepository,
                                       EmailService emailService,
                                       SmsService smsService,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${app.mail.support:support@vitc.in}") String supportEmail) {
        this.otpRepository = otpRepository;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.supportEmail = supportEmail == null || supportEmail.isBlank() ? "support@vitc.in" : supportEmail.trim();
    }

    @Override
    public PasswordRecoveryResponse requestOtp(RecoveryPortal portal, PasswordRecoveryRequest request) {
        String input = request.emailOrPhone() == null ? "" : request.emailOrPhone().trim();
        if (input.isBlank()) {
            throw new BadRequestException("Please enter your registered email address or phone number");
        }

        AccountMatch match = findAccount(portal, input);

        if (match == null) {
            // Anti-enumeration: Return generic response with dummy recovery token so response time & body look identical
            String dummyToken = UUID.randomUUID().toString().replace("-", "");
            return new PasswordRecoveryResponse(
                    dummyToken,
                    maskContact(input),
                    RESEND_COOLDOWN_SECONDS,
                    OTP_EXPIRY_MINUTES,
                    input.contains("@") ? DeliveryChannel.EMAIL : DeliveryChannel.SMS
            );
        }

        // Invalidate any previous unverified OTPs for this account
        List<PasswordResetOtp> existing = otpRepository.findByAccountIdAndAccountRoleAndUsedFalse(match.accountId(), portal);
        for (PasswordResetOtp old : existing) {
            old.setUsed(true);
        }
        otpRepository.saveAll(existing);

        // Generate 6-digit numeric OTP server-side
        String otp = generateNumericOtp();
        String otpHash = passwordEncoder.encode(otp);
        String recoveryToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();

        PasswordResetOtp otpRecord = PasswordResetOtp.builder()
                .accountId(match.accountId())
                .accountRole(portal)
                .targetContact(match.targetContact())
                .deliveryChannel(match.channel())
                .otpHash(otpHash)
                .recoveryToken(recoveryToken)
                .lastSentAt(now)
                .expiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES))
                .attemptCount(0)
                .used(false)
                .build();

        otpRepository.save(otpRecord);

        // Dispatch OTP via Email or SMS
        dispatchOtp(match.targetContact(), match.fullName(), otp, match.channel(), portal);

        return new PasswordRecoveryResponse(
                recoveryToken,
                maskContact(match.targetContact()),
                RESEND_COOLDOWN_SECONDS,
                OTP_EXPIRY_MINUTES,
                match.channel()
        );
    }

    @Override
    public VerifyOtpResponse verifyOtp(RecoveryPortal portal, VerifyOtpRequest request) {
        String token = request.recoveryToken() == null ? "" : request.recoveryToken().trim();
        String submittedOtp = request.otp() == null ? "" : request.otp().trim();

        PasswordResetOtp otpEntity = otpRepository.findByRecoveryTokenAndAccountRole(token, portal)
                .orElseThrow(() -> new BadRequestException("Invalid or expired recovery session. Please request a new OTP."));

        if (Boolean.TRUE.equals(otpEntity.getUsed())) {
            throw new BadRequestException("This recovery session has already been used. Please request a new OTP.");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This OTP has expired. Please request a new OTP.");
        }

        if (otpEntity.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BadRequestException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        boolean matches = passwordEncoder.matches(submittedOtp, otpEntity.getOtpHash());
        if (!matches) {
            int attempts = otpEntity.getAttemptCount() + 1;
            otpEntity.setAttemptCount(attempts);
            otpRepository.save(otpEntity);

            if (attempts >= MAX_ATTEMPTS) {
                throw new BadRequestException("Maximum verification attempts exceeded. Please request a new OTP.");
            }
            int remaining = MAX_ATTEMPTS - attempts;
            throw new BadRequestException("Incorrect OTP. (" + remaining + " " + (remaining == 1 ? "attempt" : "attempts") + " remaining)");
        }

        // OTP is valid -> issue a short-lived reset authorization token
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        otpEntity.setResetToken(resetToken);
        otpEntity.setVerifiedAt(LocalDateTime.now());
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otpEntity);

        return new VerifyOtpResponse(resetToken, OTP_EXPIRY_MINUTES);
    }

    @Override
    public PasswordRecoveryResponse resendOtp(RecoveryPortal portal, ResendOtpRequest request) {
        String token = request.recoveryToken() == null ? "" : request.recoveryToken().trim();

        PasswordResetOtp otpEntity = otpRepository.findByRecoveryTokenAndAccountRole(token, portal)
                .orElseThrow(() -> new BadRequestException("Invalid recovery session. Please request a new OTP."));

        if (Boolean.TRUE.equals(otpEntity.getUsed())) {
            throw new BadRequestException("This recovery session has already been used. Please start over.");
        }

        long secondsSinceLastSent = ChronoUnit.SECONDS.between(otpEntity.getLastSentAt(), LocalDateTime.now());
        if (secondsSinceLastSent < RESEND_COOLDOWN_SECONDS) {
            long waitTime = RESEND_COOLDOWN_SECONDS - secondsSinceLastSent;
            throw new BadRequestException("Please wait " + waitTime + " seconds before requesting a new OTP.");
        }

        // Resolve recipient name for email personalization
        String recipientName = resolveRecipientName(portal, otpEntity.getAccountId());

        // Generate NEW OTP
        String newOtp = generateNumericOtp();
        String newOtpHash = passwordEncoder.encode(newOtp);
        LocalDateTime now = LocalDateTime.now();

        otpEntity.setOtpHash(newOtpHash);
        otpEntity.setAttemptCount(0);
        otpEntity.setLastSentAt(now);
        otpEntity.setExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otpEntity);

        dispatchOtp(otpEntity.getTargetContact(), recipientName, newOtp, otpEntity.getDeliveryChannel(), portal);

        return new PasswordRecoveryResponse(
                otpEntity.getRecoveryToken(),
                maskContact(otpEntity.getTargetContact()),
                RESEND_COOLDOWN_SECONDS,
                OTP_EXPIRY_MINUTES,
                otpEntity.getDeliveryChannel()
        );
    }

    @Override
    public void resetPassword(RecoveryPortal portal, CompletePasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        if (request.newPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }

        String resetToken = request.resetToken() == null ? "" : request.resetToken().trim();
        PasswordResetOtp otpEntity = otpRepository.findByResetTokenAndAccountRole(resetToken, portal)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset authorization. Please request a new OTP."));

        if (otpEntity.getVerifiedAt() == null) {
            throw new BadRequestException("OTP has not been verified yet.");
        }

        if (Boolean.TRUE.equals(otpEntity.getUsed())) {
            throw new BadRequestException("This password reset authorization has already been used.");
        }

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset authorization has expired. Please request a new OTP.");
        }

        String encodedPassword = passwordEncoder.encode(request.newPassword());

        switch (portal) {
            case ADMIN -> {
                Admin admin = adminRepository.findById(otpEntity.getAccountId())
                        .orElseThrow(() -> new BadRequestException("Admin account not found"));
                admin.setPasswordHash(encodedPassword);
                // Invalidate any active session tokens
                admin.setSessionToken(null);
                admin.setSessionExpiresAt(null);
                admin.setResetToken(null);
                admin.setResetTokenExpiresAt(null);
                adminRepository.saveAndFlush(admin);

                log.info("OTP password reset account: portal={}, accountId={}, username={}, email={}, role={}",
                        portal, admin.getId(), admin.getUsername(), admin.getEmail(), admin.getRole());

                Admin savedAdmin = adminRepository.findById(admin.getId()).orElseThrow();
                boolean passwordMatches = passwordEncoder.matches(request.newPassword(), savedAdmin.getPasswordHash());
                if (!passwordMatches) {
                    throw new IllegalStateException("Password reset verification failed for admin account " + savedAdmin.getId());
                }
            }
            case TEACHER -> {
                User teacher = userRepository.findById(otpEntity.getAccountId())
                        .orElseThrow(() -> new BadRequestException("Teacher account not found"));
                if (teacher.getRole() != UserRole.TEACHER) {
                    throw new BadRequestException("Account is not a teacher");
                }
                teacher.setPasswordHash(encodedPassword);
                teacher.setMustChangePassword(false);
                teacher.setSessionToken(null);
                teacher.setSessionExpiresAt(null);
                teacher.setResetToken(null);
                teacher.setResetTokenExpiresAt(null);
                userRepository.saveAndFlush(teacher);

                log.info("OTP password reset account: portal={}, accountId={}, username={}, email={}, role={}",
                        portal, teacher.getId(), teacher.getUsername(), teacher.getEmail(), teacher.getRole());

                User savedTeacher = userRepository.findById(teacher.getId()).orElseThrow();
                boolean passwordMatches = passwordEncoder.matches(request.newPassword(), savedTeacher.getPasswordHash());
                if (!passwordMatches) {
                    throw new IllegalStateException("Password reset verification failed for teacher account " + savedTeacher.getId());
                }
            }
            case STUDENT -> {
                User student = userRepository.findById(otpEntity.getAccountId())
                        .orElseThrow(() -> new BadRequestException("Student account not found"));
                if (student.getRole() != UserRole.STUDENT) {
                    throw new BadRequestException("Account is not a student");
                }
                student.setPasswordHash(encodedPassword);
                student.setMustChangePassword(false);
                student.setSessionToken(null);
                student.setSessionExpiresAt(null);
                student.setResetToken(null);
                student.setResetTokenExpiresAt(null);
                userRepository.saveAndFlush(student);

                log.info("OTP password reset account: portal={}, accountId={}, studentLoginId={}, email={}, role={}",
                        portal, student.getId(), student.getStudentLoginId(), student.getEmail(), student.getRole());

                User savedStudent = userRepository.findById(student.getId()).orElseThrow();
                boolean passwordMatches = passwordEncoder.matches(request.newPassword(), savedStudent.getPasswordHash());
                if (!passwordMatches) {
                    throw new IllegalStateException("Password reset verification failed for student account " + savedStudent.getId());
                }
            }
        }

        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);
    }

    /* ------------------------------ Internal Helpers ------------------------------ */

    private void dispatchOtp(String targetContact, String recipientName, String otp, DeliveryChannel channel, RecoveryPortal portal) {
        if (channel == DeliveryChannel.EMAIL) {
            if (emailService.isConfigured()) {
                String html = OtpEmailTemplates.buildOtpEmail(recipientName, otp, OTP_EXPIRY_MINUTES, portal, supportEmail);
                try {
                    emailService.sendHtml(targetContact, "Password Reset OTP — VITC", html);
                    log.info("Password reset OTP dispatched via Email to {}", maskContact(targetContact));
                } catch (Exception ex) {
                    log.warn("Failed to dispatch OTP email to {}: {}", maskContact(targetContact), ex.getMessage());
                }
            } else {
                log.warn("SMTP not configured. Generated OTP for [{}]: {}", maskContact(targetContact), otp);
            }
        } else if (channel == DeliveryChannel.SMS) {
            smsService.sendOtp(targetContact, otp, OTP_EXPIRY_MINUTES);
            log.info("Password reset OTP dispatched via SMS to {}", maskContact(targetContact));
        }
    }

    private String resolveRecipientName(RecoveryPortal portal, Long accountId) {
        if (portal == RecoveryPortal.ADMIN) {
            return adminRepository.findById(accountId).map(Admin::getFullName).orElse("Admin");
        } else {
            return userRepository.findById(accountId).map(User::getFullName).orElse("User");
        }
    }

    private AccountMatch findAccount(RecoveryPortal portal, String input) {
        boolean isEmail = input.contains("@");
        boolean isPhone = isPhoneNumber(input);

        switch (portal) {
            case ADMIN -> {
                Admin admin = adminRepository.findByUsernameIgnoreCase(input)
                        .or(() -> adminRepository.findByEmailIgnoreCase(input))
                        .orElse(null);
                if (admin == null || !Boolean.TRUE.equals(admin.getActive())) {
                    return null;
                }
                return new AccountMatch(
                        admin.getId(),
                        admin.getFullName(),
                        admin.getEmail(),
                        DeliveryChannel.EMAIL
                );
            }
            case TEACHER -> {
                User teacher = userRepository.findByUsernameIgnoreCase(input)
                        .or(() -> userRepository.findByEmailIgnoreCase(input))
                        .or(() -> userRepository.findByPhone(input))
                        .filter(u -> u.getRole() == UserRole.TEACHER)
                        .orElse(null);
                if (teacher == null) {
                    return null;
                }
                DeliveryChannel channel = (isPhone && teacher.getPhone() != null && !teacher.getPhone().isBlank())
                        ? DeliveryChannel.SMS
                        : DeliveryChannel.EMAIL;
                String targetContact = channel == DeliveryChannel.SMS ? teacher.getPhone() : teacher.getEmail();
                if (targetContact == null || targetContact.isBlank()) {
                    targetContact = teacher.getEmail();
                    channel = DeliveryChannel.EMAIL;
                }
                return new AccountMatch(
                        teacher.getId(),
                        teacher.getFullName(),
                        targetContact,
                        channel
                );
            }
            case STUDENT -> {
                User student = userRepository.findByStudentLoginIdIgnoreCase(input)
                        .or(() -> userRepository.findByEmailIgnoreCase(input))
                        .or(() -> userRepository.findByPhone(input))
                        .or(() -> userRepository.findByUsernameIgnoreCase(input))
                        .filter(u -> u.getRole() == UserRole.STUDENT)
                        .orElse(null);
                if (student == null) {
                    return null;
                }
                DeliveryChannel channel = (isPhone && student.getPhone() != null && !student.getPhone().isBlank())
                        ? DeliveryChannel.SMS
                        : DeliveryChannel.EMAIL;
                String targetContact = channel == DeliveryChannel.SMS ? student.getPhone() : student.getEmail();
                if (targetContact == null || targetContact.isBlank()) {
                    targetContact = student.getEmail();
                    channel = DeliveryChannel.EMAIL;
                }
                return new AccountMatch(
                        student.getId(),
                        student.getFullName(),
                        targetContact,
                        channel
                );
            }
        }
        return null;
    }

    private static boolean isPhoneNumber(String str) {
        String cleaned = str.replaceAll("[\\s\\-+()]", "");
        return cleaned.matches("^\\d{7,15}$");
    }

    private static String generateNumericOtp() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    private static String maskContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return "your registered contact";
        }
        if (contact.contains("@")) {
            String[] parts = contact.split("@", 2);
            String name = parts[0];
            String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
            return visible + "*".repeat(Math.max(1, name.length() - visible.length())) + "@" + parts[1];
        }
        if (contact.length() > 4) {
            return "******" + contact.substring(contact.length() - 4);
        }
        return "***";
    }

    private record AccountMatch(
            Long accountId,
            String fullName,
            String targetContact,
            DeliveryChannel channel
    ) {
    }
}

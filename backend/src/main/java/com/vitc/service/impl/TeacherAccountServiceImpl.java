package com.vitc.service.impl;

import com.vitc.dto.request.TeacherForgotPasswordRequest;
import com.vitc.dto.request.TeacherLoginRequest;
import com.vitc.dto.request.TeacherPasswordChangeRequest;
import com.vitc.dto.request.TeacherSelfResetPasswordRequest;
import com.vitc.dto.request.TeacherSessionVerifyRequest;
import com.vitc.dto.response.TeacherForgotPasswordResponse;
import com.vitc.dto.response.TeacherProfileResponse;
import com.vitc.dto.response.TeacherSessionResponse;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.repository.UserRepository;
import com.vitc.security.TokenSecurityUtil;
import com.vitc.service.EmailService;
import com.vitc.service.TeacherAccountService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher Admin authentication foundation. Mirrors the session model already
 * used by {@code StudentAccountServiceImpl} (opaque token + expiry stored on
 * the shared {@code users} row) so no second session mechanism is
 * introduced. Nothing here ever returns {@code passwordHash} to a caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherAccountServiceImpl implements TeacherAccountService {

    private static final long SESSION_HOURS = 8;
    private static final long RESET_MINUTES = 20;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.mail.support:support@vitc.in}")
    private String supportEmail = "support@vitc.in";

    @Value("${app.teacher.login-url:http://localhost:8080/teacher-login.html}")
    private String teacherLoginUrl = "http://localhost:8080/teacher-login.html";

    @Override
    @Transactional
    public TeacherSessionResponse login(TeacherLoginRequest request) {
        String identifier = request.username() == null ? "" : request.username().trim();
        User user = findTeacherAccount(identifier);
        log.info("Teacher login attempt: identifier={}, foundTeacherId={}, status={}, role={}",
                identifier, user.getId(), user.getStatus(), user.getRole());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("This teacher account is not active. Please contact the administrator.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid username or password");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        user.setSessionToken(token);
        user.setSessionExpiresAt(LocalDateTime.now().plusHours(SESSION_HOURS));
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        return new TeacherSessionResponse(token, Boolean.TRUE.equals(user.getMustChangePassword()), toProfile(user));
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherProfileResponse verifySession(TeacherSessionVerifyRequest request) {
        String identifier = request.username() == null ? "" : request.username().trim();
        User user = findTeacherAccount(identifier);
        if (user.getStatus() != UserStatus.ACTIVE
                || user.getSessionToken() == null
                || !TokenSecurityUtil.matches(user.getSessionToken(), request.token())
                || user.getSessionExpiresAt() == null
                || user.getSessionExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Session expired, please sign in again");
        }
        return toProfile(user);
    }

    private User findTeacherAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Invalid username or password");
        }
        return userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .or(() -> userRepository.findByPhone(identifier))
                .filter(u -> u.getRole() == UserRole.TEACHER)
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));
    }

    @Override
    @Transactional
    public void logout(Long teacherId) {
        userRepository.findById(teacherId).ifPresent(user -> {
            user.setSessionToken(null);
            user.setSessionExpiresAt(null);
            userRepository.save(user);
        });
    }

    @Override
    public TeacherProfileResponse profile(Long teacherId) {
        return toProfile(userRepository.findById(teacherId)
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again")));
    }

    @Override
    @Transactional
    public TeacherSessionResponse changePassword(Long teacherId, TeacherPasswordChangeRequest request) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again"));
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        String token = UUID.randomUUID().toString().replace("-", "");
        user.setSessionToken(token);
        user.setSessionExpiresAt(LocalDateTime.now().plusHours(SESSION_HOURS));
        user = userRepository.save(user);

        return new TeacherSessionResponse(token, false, toProfile(user));
    }

    @Override
    @Transactional
    public TeacherForgotPasswordResponse forgotPassword(TeacherForgotPasswordRequest request) {
        String key = request.usernameOrEmail() == null ? "" : request.usernameOrEmail().trim();
        User user = userRepository.findByUsernameIgnoreCase(key)
                .or(() -> userRepository.findByEmailIgnoreCase(key))
                .filter(u -> u.getRole() == UserRole.TEACHER)
                .orElseThrow(() -> new BadRequestException("No teacher account matches that username or email"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("This teacher account is not active. Please contact the administrator.");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(RESET_MINUTES);
        user.setResetToken(token);
        user.setResetTokenExpiresAt(expiry);
        user = userRepository.save(user);

        if (emailService.isConfigured() && user.getEmail() != null && user.getEmail().contains("@")) {
            try {
                String html = StudentEmailTemplates.resetCode(
                        user.getFullName(), token, RESET_MINUTES, teacherLoginUrl, supportEmail);
                emailService.sendHtml(user.getEmail(), "VITC Teacher Portal — Password reset code", html);
                log.info("Password reset code issued and emailed for teacher {}", user.getUsername());
            } catch (Exception ex) {
                log.warn("Could not email password reset code to teacher {}: {}", user.getEmail(), ex.getMessage());
            }
        } else {
            log.info("Reset code issued for teacher {}: {}", user.getUsername(), token);
        }

        return new TeacherForgotPasswordResponse(maskEmail(user.getEmail()), token, (int) RESET_MINUTES);
    }

    @Override
    @Transactional
    public void resetPassword(TeacherSelfResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }
        if (request.newPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters.");
        }
        String code = request.resetCode() == null ? "" : request.resetCode().trim();
        User user = userRepository.findByResetTokenAndRole(code, UserRole.TEACHER)
                .orElseThrow(() -> new BadRequestException("This password reset link is no longer valid. Please request a new one."));

        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            user.setResetToken(null);
            user.setResetTokenExpiresAt(null);
            userRepository.save(user);
            throw new BadRequestException("This password reset link has expired. Please request a new one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        // Force a re-login everywhere after a password reset.
        user.setSessionToken(null);
        user.setSessionExpiresAt(null);
        userRepository.save(user);
        log.info("Password reset completed for teacher {}", user.getUsername());
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "your registered email";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "***@" + parts[1];
    }

    private TeacherProfileResponse toProfile(User user) {
        return new TeacherProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getLastLoginAt());
    }
}

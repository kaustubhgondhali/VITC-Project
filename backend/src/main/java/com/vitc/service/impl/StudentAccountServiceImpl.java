package com.vitc.service.impl;

import com.vitc.dto.request.StudentForgotPasswordRequest;
import com.vitc.dto.request.StudentLoginRequest;
import com.vitc.dto.request.StudentPasswordChangeRequest;
import com.vitc.dto.request.StudentProfileImageUpdateRequest;
import com.vitc.dto.request.StudentProfileUpdateRequest;
import com.vitc.dto.request.StudentResetPasswordRequest;
import com.vitc.dto.response.StudentCourseResponse;
import com.vitc.dto.response.StudentCredentialsResponse;
import com.vitc.dto.response.StudentForgotPasswordResponse;
import com.vitc.dto.response.StudentProfileResponse;
import com.vitc.dto.response.StudentSessionResponse;
import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;
import com.vitc.entity.PaymentOrder;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.EmailService;
import com.vitc.service.StudentAccountService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentAccountServiceImpl implements StudentAccountService {

    private static final String ID_PREFIX = "VITCSTU";
    private static final long ID_START = 10001L;
    private static final long SESSION_HOURS = 8;
    private static final String PW_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** Part 4 - unambiguous alphabet (no 0/O/1/I) for reset codes read out of an email. */
    private static final String RESET_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RESET_CODE_LENGTH = 8;
    private static final long RESET_MINUTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.student.login-url:http://localhost:5500/student-login.html}")
    private String loginUrl;

    @Value("${app.mail.support-email:support@vitc.local}")
    private String supportEmail;

    /* ============================ Provisioning ============================ */

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public StudentCredentialsResponse provisionForPaidOrder(PaymentOrder order) {
        if (order == null || !"COURSE".equalsIgnoreCase(order.getItemType())) {
            return null; // only course purchases create student access
        }
        String email = order.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Paid order {} has no email; no student account provisioned", order.getOrderCode());
            return null;
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        String temporaryPassword = null;
        boolean newAccount = false;

        if (user == null) {
            temporaryPassword = generatePassword();
            user = User.builder()
                    .fullName(order.getCustomerName())
                    .email(email.trim())
                    .phone(order.getPhone())
                    .city(order.getCity())
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .role(UserRole.STUDENT)
                    .status(UserStatus.ACTIVE)
                    .studentLoginId(nextStudentLoginId())
                    .mustChangePassword(true)
                    .build();
            user = userRepository.save(user);
            newAccount = true;
            log.info("Student account {} created from paid order {}", user.getStudentLoginId(), order.getOrderCode());
        } else {
            boolean dirty = false;
            if (user.getStudentLoginId() == null || user.getStudentLoginId().isBlank()) {
                // An existing (e.g. admin-created) account buying a course gets a
                // login id, but its role and password are left untouched.
                user.setStudentLoginId(nextStudentLoginId());
                dirty = true;
            }
            if (user.getStatus() == UserStatus.INACTIVE) {
                user.setStatus(UserStatus.ACTIVE);
                dirty = true;
            }
            if (dirty) {
                user = userRepository.save(user);
            }
        }

        linkEnrollment(order, user);

        return new StudentCredentialsResponse(
                user.getStudentLoginId(),
                temporaryPassword,
                newAccount,
                newAccount
                        ? "Save these credentials securely and change the password after your first login."
                        : "This course has been added to your existing student account. Use your existing password.");
    }

    /** Creates or activates the enrolment for the purchased course. */
    private void linkEnrollment(PaymentOrder order, User user) {
        Course course = resolveCourse(order);
        if (course == null) {
            log.warn("Paid order {} could not be matched to a course; enrolment not linked", order.getOrderCode());
            return;
        }
        Enrollment enrollment = enrollmentRepository
                .findFirstByUserIdAndCourseId(user.getId(), course.getId())
                .or(() -> enrollmentRepository
                        .findFirstByEmailIgnoreCaseAndCourseIdOrderByIdDesc(user.getEmail(), course.getId()))
                .orElse(null);

        if (enrollment == null) {
            enrollment = Enrollment.builder()
                    .studentName(user.getFullName())
                    .email(user.getEmail())
                    .phone(user.getPhone() == null || user.getPhone().isBlank() ? "-" : user.getPhone())
                    .course(course)
                    .user(user)
                    .amount(order.getTotalAmount())
                    .status(EnrollmentStatus.ACTIVE)
                    .notes("Auto-created from paid order " + order.getOrderCode())
                    .build();
        } else {
            enrollment.setUser(user);
            if (enrollment.getStatus() == EnrollmentStatus.PENDING
                    || enrollment.getStatus() == EnrollmentStatus.CONFIRMED) {
                enrollment.setStatus(EnrollmentStatus.ACTIVE);
            }
        }
        enrollmentRepository.save(enrollment);
    }

    /**
     * PART 14 - repairs legacy/admin-created enrolments (created before this student's
     * account existed, or added directly via the Admin panel rather than through
     * checkout) so a real purchase is never invisible in My Courses. Only ever links an
     * enrolment where {@code enrollment.getUser() == null} AND the enrolment's email
     * matches THIS student's email, case-insensitively - identity is decided on email
     * alone, never name/phone/course, and an enrolment already linked to a (possibly
     * different) student account is never reassigned. Safe to run on every login: a
     * student with nothing unlinked is a no-op, and running it twice links nothing a
     * second time. Mirrored by {@code StudentLearningServiceImpl.reconcileStudentEnrollments}
     * so the same repair also happens transparently on My Courses / course access for a
     * student whose session outlives this login call.
     */
    private void reconcileStudentEnrollments(User student) {
        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        List<Enrollment> unlinked = enrollmentRepository.findByEmailIgnoreCase(email).stream()
                .filter(e -> e.getUser() == null)
                .toList();
        if (unlinked.isEmpty()) {
            return;
        }
        unlinked.forEach(e -> e.setUser(student));
        enrollmentRepository.saveAll(unlinked);
        log.info("Reconciled {} legacy enrolment(s) to student {} at login", unlinked.size(), student.getStudentLoginId());
    }

    private Course resolveCourse(PaymentOrder order) {
        if (order.getItemRefId() != null) {
            Course byId = courseRepository.findById(order.getItemRefId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        String title = order.getItemTitle();
        if (title == null) {
            return null;
        }
        return courseRepository.findAll().stream()
                .filter(c -> title.equalsIgnoreCase(c.getTitle()) || title.equalsIgnoreCase(c.getCode()))
                .findFirst()
                .orElse(null);
    }

    /** VITCSTU10001, VITCSTU10002, ... always unique. */
    private String nextStudentLoginId() {
        Long max = userRepository.findMaxStudentLoginSequence();
        long next = (max == null ? ID_START - 1 : Math.max(max, ID_START - 1)) + 1;
        String candidate = ID_PREFIX + next;
        int guard = 0;
        while (userRepository.existsByStudentLoginIdIgnoreCase(candidate) && guard++ < 1000) {
            next++;
            candidate = ID_PREFIX + next;
        }
        return candidate;
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder("VITC@");
        for (int i = 0; i < 6; i++) {
            sb.append(PW_ALPHABET.charAt(RANDOM.nextInt(PW_ALPHABET.length())));
        }
        return sb.toString();
    }

    /* ============================== Auth ============================== */

    @Override
    @Transactional
    public StudentSessionResponse login(StudentLoginRequest request) {
        String loginId = request.studentLoginId() == null ? "" : request.studentLoginId().trim();
        User user = findStudentAccount(loginId);
        log.info("Student login attempt: identifier={}, foundStudentId={}, status={}, role={}",
                loginId, user.getId(), user.getStatus(), user.getRole());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("This student account is not active. Please contact VITC support.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid Student ID or password");
        }

        reconcileStudentEnrollments(user);

        String token = UUID.randomUUID().toString().replace("-", "");
        user.setSessionToken(token);
        user.setSessionExpiresAt(LocalDateTime.now().plusHours(SESSION_HOURS));
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        return new StudentSessionResponse(token, Boolean.TRUE.equals(user.getMustChangePassword()), toProfile(user));
    }

    @Override
    @Transactional
    public void logout(Long studentId) {
        userRepository.findById(studentId).ifPresent(user -> {
            user.setSessionToken(null);
            user.setSessionExpiresAt(null);
            userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public StudentProfileResponse profile(Long studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again"));
        // PART 14 - GET /api/v1/student/me is fetched independently of login (e.g. on every
        // dashboard boot within an existing 8-hour session), so it repairs legacy enrolment
        // linkage too, not just the login call - a long-lived session must not have to wait
        // for the next fresh sign-in to see a course an admin just added by email.
        reconcileStudentEnrollments(user);
        return toProfile(user);
    }

    @Override
    @Transactional
    public StudentSessionResponse changePassword(Long studentId, StudentPasswordChangeRequest request) {
        User user = userRepository.findById(studentId)
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

        return new StudentSessionResponse(token, false, toProfile(user));
    }

    private User findStudentAccount(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Invalid Student ID or password");
        }
        return userRepository.findByStudentLoginIdIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .or(() -> userRepository.findByPhone(identifier))
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .filter(u -> u.getRole() == UserRole.STUDENT)
                .orElseThrow(() -> new BadRequestException("Invalid Student ID or password"));
    }

    /* ============================ Forgot password (Part 4) ============================ */

    @Override
    @Transactional
    public StudentForgotPasswordResponse forgotPassword(StudentForgotPasswordRequest request) {
        String key = request.studentLoginIdOrEmail() == null ? "" : request.studentLoginIdOrEmail().trim();
        User user = userRepository.findByStudentLoginIdIgnoreCase(key)
                .or(() -> userRepository.findByEmailIgnoreCase(key))
                .filter(u -> u.getRole() == UserRole.STUDENT)
                .orElseThrow(() -> new BadRequestException("No student account matches that Student ID or email"));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("This account has no registered email. Please contact VITC support.");
        }

        String code = generateResetCode();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(RESET_MINUTES);
        user.setResetToken(code);
        user.setResetTokenExpiresAt(expiry);
        user = userRepository.save(user);

        if (emailService.isConfigured()) {
            String html = StudentEmailTemplates.resetCode(
                    user.getFullName(), code, RESET_MINUTES, loginUrl, supportEmail);
            emailService.sendHtml(user.getEmail(), "VITC Student Portal — Password reset code", html);
            log.info("Password reset code issued and emailed for student {}", user.getStudentLoginId());
        } else {
            log.warn("SMTP not configured. Generated reset code for student {}: {}", user.getStudentLoginId(), code);
        }

        return new StudentForgotPasswordResponse(maskEmail(user.getEmail()), (int) RESET_MINUTES);
    }

    @Override
    @Transactional
    public void resetPassword(StudentResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }
        if (request.newPassword().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters.");
        }
        String code = request.resetCode() == null ? "" : request.resetCode().trim();
        User user = userRepository.findByResetTokenAndRole(code, UserRole.STUDENT)
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
        // Force a re-login everywhere after a password reset, same as Main Admin.
        user.setSessionToken(null);
        user.setSessionExpiresAt(null);
        userRepository.save(user);
        log.info("Password reset completed for student {}", user.getStudentLoginId());
    }

    private String generateResetCode() {
        StringBuilder sb = new StringBuilder(RESET_CODE_LENGTH);
        for (int i = 0; i < RESET_CODE_LENGTH; i++) {
            sb.append(RESET_CODE_ALPHABET.charAt(RANDOM.nextInt(RESET_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "your registered email";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "*".repeat(Math.max(1, name.length() - visible.length())) + "@" + parts[1];
    }

    /* ============================ Profile (Part 6) ============================ */

    @Override
    @Transactional
    public StudentProfileResponse updateProfile(Long studentId, StudentProfileUpdateRequest request) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again"));

        // Only these three fields are ever written here. studentLoginId, role,
        // status and enrolments are never read from the request - there is no
        // path in this method that could touch them.
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setCity(blankToNull(request.city()));

        user = userRepository.save(user);
        log.info("Student {} updated their profile", user.getStudentLoginId());
        return toProfile(user);
    }

    @Override
    @Transactional
    public StudentProfileResponse updateProfileImage(Long studentId, StudentProfileImageUpdateRequest request) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("Session expired, please sign in again"));

        String url = request.imageUrl() == null ? "" : request.imageUrl().trim();
        if (url.isEmpty()) {
            throw new BadRequestException("Image URL is required");
        }
        // The file itself was already validated and stored by the existing
        // FileStorageService upload endpoint; this only records the link.
        user.setProfileImageUrl(url);
        user = userRepository.save(user);
        log.info("Student {} updated their profile picture", user.getStudentLoginId());
        return toProfile(user);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /* ============================ Mapping ============================ */

    private StudentProfileResponse toProfile(User user) {
        List<StudentCourseResponse> courses = enrollmentRepository.findByUserIdOrderByIdDesc(user.getId()).stream()
                .map(e -> {
                    Course c = e.getCourse();
                    return new StudentCourseResponse(
                            e.getId(),
                            c == null ? null : c.getId(),
                            c == null ? null : c.getCode(),
                            c == null ? "Course" : c.getTitle(),
                            c == null ? null : c.getLevel(),
                            c == null ? null : c.getCategory(),
                            c == null ? null : c.getDurationMonths(),
                            e.getStatus(),
                            e.getCreatedAt());
                })
                .toList();

        return new StudentProfileResponse(
                user.getId(),
                user.getStudentLoginId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getCity(),
                user.getProfileImageUrl(),
                user.getRole() == null ? "STUDENT" : user.getRole().name(),
                user.getCreatedAt(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getLastLoginAt(),
                courses);
    }

    @SuppressWarnings("unused")
    private static String upper(String v) {
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }
}

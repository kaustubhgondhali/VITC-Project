package com.vitc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.PasswordResetOtp;
import com.vitc.entity.User;
import com.vitc.entity.enums.AdminRole;
import com.vitc.entity.enums.DeliveryChannel;
import com.vitc.entity.enums.RecoveryPortal;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.PasswordResetOtpRepository;
import com.vitc.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OtpPasswordRecoveryTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordResetOtpRepository otpRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private static final String ADMIN_USER = "otpadmintester";
    private static final String ADMIN_EMAIL = "otpmasteradmin@vitc.in";
    private static final String ADMIN_OLD_PW = "MasterAdmin@123";

    private static final String TEACHER_USER = "otpteachertester";
    private static final String TEACHER_EMAIL = "otpteacher@vitc.in";
    private static final String TEACHER_PHONE = "+919876543210";
    private static final String TEACHER_OLD_PW = "TeacherOld@123";

    private static final String STUDENT_ID = "VITCSTUOTP99999";
    private static final String STUDENT_EMAIL = "otpstudent@example.com";
    private static final String STUDENT_PHONE = "+919123456780";
    private static final String STUDENT_OLD_PW = "StudentOld@123";

    private Admin seededAdmin;
    private User seededTeacher;
    private User seededStudent;

    @BeforeEach
    void setupTestData() {
        seededAdmin = adminRepository.findByUsernameIgnoreCase(ADMIN_USER).orElseGet(() -> adminRepository.save(
                Admin.builder()
                        .username(ADMIN_USER)
                        .email(ADMIN_EMAIL)
                        .fullName("OTP Admin Tester")
                        .passwordHash(passwordEncoder.encode(ADMIN_OLD_PW))
                        .role(AdminRole.ADMIN)
                        .active(true)
                        .build()
        ));
        seededAdmin.setPasswordHash(passwordEncoder.encode(ADMIN_OLD_PW));
        seededAdmin.setActive(true);
        seededAdmin.setSessionToken("admin-live-session-token");
        seededAdmin.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        adminRepository.save(seededAdmin);

        seededTeacher = userRepository.findByUsernameIgnoreCase(TEACHER_USER).orElseGet(() -> userRepository.save(
                User.builder()
                        .username(TEACHER_USER)
                        .email(TEACHER_EMAIL)
                        .phone(TEACHER_PHONE)
                        .fullName("OTP Teacher Tester")
                        .passwordHash(passwordEncoder.encode(TEACHER_OLD_PW))
                        .role(UserRole.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build()
        ));
        seededTeacher.setPasswordHash(passwordEncoder.encode(TEACHER_OLD_PW));
        seededTeacher.setStatus(UserStatus.ACTIVE);
        seededTeacher.setSessionToken("teacher-live-session-token");
        seededTeacher.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        userRepository.save(seededTeacher);

        seededStudent = userRepository.findByStudentLoginIdIgnoreCase(STUDENT_ID).orElseGet(() -> userRepository.save(
                User.builder()
                        .studentLoginId(STUDENT_ID)
                        .email(STUDENT_EMAIL)
                        .phone(STUDENT_PHONE)
                        .fullName("OTP Student Tester")
                        .passwordHash(passwordEncoder.encode(STUDENT_OLD_PW))
                        .role(UserRole.STUDENT)
                        .status(UserStatus.ACTIVE)
                        .build()
        ));
        seededStudent.setPasswordHash(passwordEncoder.encode(STUDENT_OLD_PW));
        seededStudent.setStatus(UserStatus.ACTIVE);
        seededStudent.setSessionToken("student-live-session-token");
        seededStudent.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        userRepository.save(seededStudent);
    }

    @Test
    @DisplayName("1. Full Admin OTP Flow: Request -> Verify -> Reset Password -> Sign in with new credentials")
    void adminFullOtpPasswordRecoveryFlow() throws Exception {
        // Step 1: Request OTP
        String reqRes = mvc.perform(post("/api/v1/admins/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + ADMIN_USER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty())
                .andExpect(jsonPath("$.data.targetMasked").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInMinutes").value(5))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60))
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();

        // Verify DB state: OTP record created with BCrypt hash
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.ADMIN).orElseThrow();
        assertNotNull(otpRecord.getOtpHash());
        assertNotEquals("123456", otpRecord.getOtpHash());
        assertFalse(otpRecord.getUsed());

        // Set deterministic OTP for test verification
        String plainOtp = "741852";
        otpRecord.setOtpHash(passwordEncoder.encode(plainOtp));
        otpRepository.save(otpRecord);

        // Step 2: Verify OTP
        String verifyRes = mvc.perform(post("/api/v1/admins/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resetToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // Step 3: Reset Password
        String newPassword = "AdminNewSecurePassword@2026";
        mvc.perform(post("/api/v1/admins/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify that OTP record is marked used
        PasswordResetOtp consumedOtp = otpRepository.findByResetTokenAndAccountRole(resetToken, RecoveryPortal.ADMIN).orElseThrow();
        assertTrue(consumedOtp.getUsed());

        // Verify that previous admin session was revoked
        Admin updatedAdmin = adminRepository.findById(seededAdmin.getId()).orElseThrow();
        assertNull(updatedAdmin.getSessionToken());
        assertTrue(passwordEncoder.matches(newPassword, updatedAdmin.getPasswordHash()));

        // Step 4: Login with old password fails
        mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_OLD_PW + "\"}"))
                .andExpect(status().is4xxClientError());

        // Step 5: Login with new password succeeds using Username
        mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        // Step 6: Login with new password succeeds using Email
        mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());
    }

    @Test
    @DisplayName("2. Full Teacher OTP Flow: Request with Phone -> Verify -> Reset Password -> Sign in")
    void teacherFullOtpPasswordRecoveryFlow() throws Exception {
        // Step 1: Request OTP using Phone Number
        String reqRes = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_PHONE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty())
                .andExpect(jsonPath("$.data.channel").value("SMS"))
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();

        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.TEACHER).orElseThrow();
        assertEquals(DeliveryChannel.SMS, otpRecord.getDeliveryChannel());

        String plainOtp = "852963";
        otpRecord.setOtpHash(passwordEncoder.encode(plainOtp));
        otpRepository.save(otpRecord);

        // Step 2: Verify OTP
        String verifyRes = mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resetToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // Step 3: Reset Password
        String newPassword = "TeacherNewSecretPass@123";
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify that previous teacher session was revoked and BCrypt hash in DB matches new password
        User updatedTeacher = userRepository.findById(seededTeacher.getId()).orElseThrow();
        assertNull(updatedTeacher.getSessionToken());
        assertTrue(updatedTeacher.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(newPassword, updatedTeacher.getPasswordHash()));

        // Step 4: Login with old password fails
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USER + "\",\"password\":\"" + TEACHER_OLD_PW + "\"}"))
                .andExpect(status().is4xxClientError());

        // Step 5: Login with new password succeeds using Username
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USER + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        // Step 6: Login with new password succeeds using Email
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_EMAIL + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        // Step 7: Login with new password succeeds using Phone
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_PHONE + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());
    }

    @Test
    @DisplayName("3. Full Student OTP Flow: Request with Student ID -> Verify -> Reset Password -> Sign in")
    void studentFullOtpPasswordRecoveryFlow() throws Exception {
        // Step 1: Request OTP using Student Login ID
        String reqRes = mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"" + STUDENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();

        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.STUDENT).orElseThrow();
        String plainOtp = "963852";
        otpRecord.setOtpHash(passwordEncoder.encode(plainOtp));
        otpRepository.save(otpRecord);

        // Step 2: Verify OTP
        String verifyRes = mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resetToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // Step 3: Reset Password
        String newPassword = "StudentBrandNewSecret@123";
        mvc.perform(post("/api/v1/student/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify that previous student session was revoked and BCrypt hash in DB matches new password
        User updatedStudent = userRepository.findById(seededStudent.getId()).orElseThrow();
        assertNull(updatedStudent.getSessionToken());
        assertTrue(updatedStudent.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(newPassword, updatedStudent.getPasswordHash()));

        // Step 4: Login with old password fails
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_ID + "\",\"password\":\"" + STUDENT_OLD_PW + "\"}"))
                .andExpect(status().is4xxClientError());

        // Step 5: Login with new password succeeds using Student Login ID
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_ID + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        // Step 6: Login with new password succeeds using Email
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_EMAIL + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());

        // Step 7: Login with new password succeeds using Phone
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_PHONE + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());
    }

    @Test
    @DisplayName("4. Anti-Account Enumeration: Non-existent accounts return identical generic response")
    void antiAccountEnumeration() throws Exception {
        mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"nonexistent_random_student@vitc.in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInMinutes").value(5))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));

        mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"nonexistent_random_teacher@vitc.in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty());

        mvc.perform(post("/api/v1/admins/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"nonexistent_random_admin@vitc.in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty());
    }

    @Test
    @DisplayName("5. Incorrect OTP Feedback & Attempt Limit Lockout (5 max attempts)")
    void incorrectOtpAttemptCountingAndLockout() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/admins/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + ADMIN_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();

        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.ADMIN).orElseThrow();
        otpRecord.setOtpHash(passwordEncoder.encode("112233"));
        otpRepository.save(otpRecord);

        // Attempts 1 to 4 should return Bad Request with remaining attempts
        for (int i = 1; i <= 4; i++) {
            int remaining = 5 - i;
            mvc.perform(post("/api/v1/admins/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"999999\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Incorrect OTP. (" + remaining + " " + (remaining == 1 ? "attempt" : "attempts") + " remaining)"));
        }

        // Attempt 5 exceeds maximum attempts limit
        mvc.perform(post("/api/v1/admins/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Maximum verification attempts exceeded. Please request a new OTP."));

        // Subsequent attempt with correct OTP is now locked out
        mvc.perform(post("/api/v1/admins/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"112233\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Maximum verification attempts exceeded. Please request a new OTP."));
    }

    @Test
    @DisplayName("6. OTP Expiration: OTP past 5-minute expiry is rejected")
    void expiredOtpIsRejected() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.TEACHER).orElseThrow();
        otpRecord.setOtpHash(passwordEncoder.encode("445566"));
        // Force expiry in the past
        otpRecord.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otpRepository.save(otpRecord);

        mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"445566\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This OTP has expired. Please request a new OTP."));
    }

    @Test
    @DisplayName("7. Resend Cooldown: Enforces 60-second wait before allowing resend")
    void resendCooldownEnforced() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"" + STUDENT_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();

        // Immediate resend must fail with cooldown error
        mvc.perform(post("/api/v1/student/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Please wait")));

        // Simulate 61 seconds passing
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.STUDENT).orElseThrow();
        otpRecord.setLastSentAt(LocalDateTime.now().minusSeconds(65));
        otpRepository.save(otpRecord);

        // Resend should now succeed
        mvc.perform(post("/api/v1/student/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));
    }

    @Test
    @DisplayName("8. Single-Use Token: resetToken cannot be used more than once")
    void singleUseResetTokenEnforced() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/admins/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + ADMIN_USER + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.ADMIN).orElseThrow();
        otpRecord.setOtpHash(passwordEncoder.encode("334455"));
        otpRepository.save(otpRecord);

        String verifyRes = mvc.perform(post("/api/v1/admins/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"334455\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // First reset succeeds
        mvc.perform(post("/api/v1/admins/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NewPassFirst@123\",\"confirmPassword\":\"NewPassFirst@123\"}"))
                .andExpect(status().isOk());

        // Replay of the same resetToken must fail
        mvc.perform(post("/api/v1/admins/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NewPassSecond@123\",\"confirmPassword\":\"NewPassSecond@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This password reset authorization has already been used."));
    }

    @Test
    @DisplayName("9. Role Isolation: Student recovery token cannot be verified on Teacher or Admin endpoint")
    void crossRoleIsolationEnforced() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"" + STUDENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String studentRecoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(studentRecoveryToken, RecoveryPortal.STUDENT).orElseThrow();
        otpRecord.setOtpHash(passwordEncoder.encode("123987"));
        otpRepository.save(otpRecord);

        // Attempting to verify student recoveryToken on Teacher endpoint
        mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + studentRecoveryToken + "\",\"otp\":\"123987\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired recovery session. Please request a new OTP."));

        // Attempting to verify student recoveryToken on Admin endpoint
        mvc.perform(post("/api/v1/admins/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + studentRecoveryToken + "\",\"otp\":\"123987\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired recovery session. Please request a new OTP."));
    }

    @Test
    @DisplayName("10. Password Policy Validation: Rejects passwords shorter than 8 chars and mismatches")
    void passwordPolicyValidation() throws Exception {
        String reqRes = mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"" + STUDENT_ID + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = objectMapper.readTree(reqRes).path("data").path("recoveryToken").asText();
        PasswordResetOtp otpRecord = otpRepository.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.STUDENT).orElseThrow();
        otpRecord.setOtpHash(passwordEncoder.encode("778899"));
        otpRepository.save(otpRecord);

        String verifyRes = mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"778899\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // Mismatched passwords
        mvc.perform(post("/api/v1/student/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"ValidPass@123\",\"confirmPassword\":\"DifferentPass@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password and confirm password do not match"));

        // Short password (< 8 chars)
        mvc.perform(post("/api/v1/student/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("11. OTP Format Validation: Non-6-digit or non-numeric OTP rejected")
    void otpFormatValidation() throws Exception {
        mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"some-token\",\"otp\":\"12345\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"some-token\",\"otp\":\"abcdef\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"some-token\",\"otp\":\"1234567\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. Invalidating Old Pending OTPs on New Request")
    void invalidatesOldPendingOtpsOnNewRequest() throws Exception {
        // First request
        String res1 = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token1 = objectMapper.readTree(res1).path("data").path("recoveryToken").asText();

        // Second request
        String res2 = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token2 = objectMapper.readTree(res2).path("data").path("recoveryToken").asText();

        assertNotEquals(token1, token2);

        // Old token1 must now be marked used/invalid
        PasswordResetOtp oldOtp = otpRepository.findByRecoveryTokenAndAccountRole(token1, RecoveryPortal.TEACHER).orElseThrow();
        assertTrue(oldOtp.getUsed());

        // Attempting to verify with old token1 must fail
        mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + token1 + "\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This recovery session has already been used. Please request a new OTP."));
    }
}


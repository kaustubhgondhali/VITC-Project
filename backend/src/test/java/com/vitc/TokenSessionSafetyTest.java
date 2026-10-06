package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;
import com.vitc.entity.PasswordResetOtp;
import com.vitc.entity.User;
import com.vitc.entity.enums.DeliveryChannel;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.RecoveryPortal;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.PasswordResetOtpRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 2B-3/7 - Authentication &amp; Token/Session Safety.
 *
 * <p>End-to-end (real HTTP, real DB, no mocking of the auth layer) verification that every
 * protected surface - Student, Teacher, Main Admin - rejects a missing, malformed, tampered,
 * invalid-signature-equivalent, and expired session token, and accepts a genuinely valid one.
 * Also covers that a password-reset token cannot be reused once consumed. This complements
 * {@code AuthenticationCoreSystemTest} (login journeys) and the existing role/authorization
 * suites; it does not repeat their coverage.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TokenSessionSafetyTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;
    @Autowired PasswordResetOtpRepository otps;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private static final String ADMIN_USERNAME = "tokensafetyadmin";
    private static final String ADMIN_PASSWORD = "TokenSafety@Admin1";

    private static final String TEACHER_USERNAME = "tokensafetyteacher";
    private static final String TEACHER_PASSWORD = "TokenSafety@Teacher1";

    private static final String STUDENT_LOGIN_ID = "VITCSTUTOKSAFE01";
    private static final String STUDENT_PASSWORD = "TokenSafety@Student1";

    private Admin admin;
    private User teacher;
    private User student;

    @BeforeEach
    void seed() {
        admin = admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("tokensafetyadmin@test.io").fullName("Token Safety Admin")
                .passwordHash(encoder.encode(ADMIN_PASSWORD)).active(true).build()));

        teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(User.builder()
                .fullName("Token Safety Teacher").email("tokensafetyteacher@test.io")
                .username(TEACHER_USERNAME)
                .passwordHash(encoder.encode(TEACHER_PASSWORD))
                .role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));

        Course course = courses.findByCode("TOKSAFECOURSE").orElseGet(() -> courses.save(Course.builder()
                .code("TOKSAFECOURSE").title("Token Safety Course").price(BigDecimal.TEN).active(true).build()));
        student = users.findByStudentLoginIdIgnoreCase(STUDENT_LOGIN_ID).orElseGet(() -> users.save(User.builder()
                .fullName("Token Safety Student").email("tokensafetystudent@test.io")
                .studentLoginId(STUDENT_LOGIN_ID)
                .passwordHash(encoder.encode(STUDENT_PASSWORD))
                .role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
        if (enrollments.findFirstByUserIdAndCourseId(student.getId(), course.getId()).isEmpty()) {
            enrollments.save(Enrollment.builder().studentName("Token Safety Student").email(student.getEmail())
                    .phone("-").course(course).user(student).amount(BigDecimal.ONE)
                    .status(EnrollmentStatus.ACTIVE).build());
        }
    }

    /* =======================================================================================
     * STUDENT - GET /api/v1/student/me
     * ======================================================================================= */

    @Test
    void studentProtectedApiRejectsMissingToken() throws Exception {
        mvc.perform(get("/api/v1/student/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentProtectedApiRejectsMalformedAndTamperedAndInvalidToken() throws Exception {
        String live = issueStudentSession();

        // Malformed - not the token shape the app ever issues.
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", "not-a-real-token"))
                .andExpect(status().isUnauthorized());

        // Tampered - a live, valid token with one character flipped.
        String tampered = flipLastChar(live);
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", tampered))
                .andExpect(status().isUnauthorized());

        // Invalid - well-formed but never issued.
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID)
                        .header("X-Student-Token", "0000000000000000000000000000aa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentProtectedApiRejectsExpiredTokenAndAllowsValidOne() throws Exception {
        String live = issueStudentSession();

        // Confirm it works first.
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", live))
                .andExpect(status().isOk());

        // Force-expire the same token server-side (simulates time passing) and retry.
        User row = users.findById(student.getId()).orElseThrow();
        row.setSessionExpiresAt(LocalDateTime.now().minusMinutes(1));
        users.save(row);

        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", live))
                .andExpect(status().isUnauthorized());
    }

    /* =======================================================================================
     * TEACHER - GET /api/v1/teacher/me
     * ======================================================================================= */

    @Test
    void teacherProtectedApiRejectsMissingMalformedTamperedExpiredTokenAndAllowsValidOne() throws Exception {
        mvc.perform(get("/api/v1/teacher/me"))
                .andExpect(status().isUnauthorized());

        String live = issueTeacherSession();

        mvc.perform(get("/api/v1/teacher/me")
                        .header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", "garbage"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/teacher/me")
                        .header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", flipLastChar(live)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/teacher/me")
                        .header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", live))
                .andExpect(status().isOk());

        User row = users.findById(teacher.getId()).orElseThrow();
        row.setSessionExpiresAt(LocalDateTime.now().minusSeconds(1));
        users.save(row);
        mvc.perform(get("/api/v1/teacher/me")
                        .header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", live))
                .andExpect(status().isUnauthorized());
    }

    /* =======================================================================================
     * MAIN ADMIN - POST /api/v1/admins/session (verifySession) and a protected admin API
     * ======================================================================================= */

    @Test
    void adminProtectedApiRejectsMissingMalformedTamperedExpiredTokenAndAllowsValidOne() throws Exception {
        mvc.perform(get("/api/v1/admin/teachers"))
                .andExpect(status().isUnauthorized());

        String live = issueAdminSession();

        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", "garbage"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", flipLastChar(live)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", live))
                .andExpect(status().isOk());

        Admin row = admins.findById(admin.getId()).orElseThrow();
        row.setSessionExpiresAt(LocalDateTime.now().minusMinutes(1));
        admins.save(row);
        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", live))
                .andExpect(status().isForbidden());
    }

    /* =======================================================================================
     * CLIENT-SIDE TAMPERING CANNOT FORGE IDENTITY
     * ======================================================================================= */

    @Test
    void studentIdHeaderCannotBeSwappedToAnotherAccountUsingSomeoneElsesToken() throws Exception {
        // A live student session belongs strictly to the account it was issued to - presenting
        // it under a different login id must not resolve to that other account.
        String live = issueStudentSession();
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", "some-other-student-id").header("X-Student-Token", live))
                .andExpect(status().isUnauthorized());
    }

    /* =======================================================================================
     * RESET TOKENS: single-use, cannot be replayed after a successful reset
     * ======================================================================================= */

    @Test
    void studentResetCodeCannotBeReusedAfterASuccessfulReset() throws Exception {
        String res = mvc.perform(post("/api/v1/student/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginIdOrEmail\":\"" + STUDENT_LOGIN_ID + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = mapper.readTree(res).path("data").path("recoveryToken").asText();

        PasswordResetOtp otpRecord = otps.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.STUDENT).orElseThrow();
        String plainOtp = "123456";
        otpRecord.setOtpHash(encoder.encode(plainOtp));
        otps.save(otpRecord);

        String verifyRes = mvc.perform(post("/api/v1/student/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resetToken = mapper.readTree(verifyRes).path("data").path("resetToken").asText();

        String firstAttempt = "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NewTokenSafety@1\","
                + "\"confirmPassword\":\"NewTokenSafety@1\"}";
        mvc.perform(post("/api/v1/student/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(firstAttempt))
                .andExpect(status().isOk());

        // Replaying the exact same code a second time must fail - it was consumed above.
        String secondAttempt = "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"AnotherOne@1\","
                + "\"confirmPassword\":\"AnotherOne@1\"}";
        mvc.perform(post("/api/v1/student/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(secondAttempt))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    /* =======================================================================================
     * helpers
     * ======================================================================================= */

    private String issueStudentSession() throws Exception {
        String body = "{\"studentLoginId\":\"" + STUDENT_LOGIN_ID + "\",\"password\":\"" + STUDENT_PASSWORD + "\"}";
        String response = mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(response);
    }

    private String issueTeacherSession() throws Exception {
        String body = "{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + TEACHER_PASSWORD + "\"}";
        String response = mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(response);
    }

    private String issueAdminSession() throws Exception {
        String body = "{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}";
        String response = mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extractToken(response);
    }

    private String extractToken(String jsonBody) {
        int idx = jsonBody.indexOf("\"sessionToken\":\"");
        int start = idx + "\"sessionToken\":\"".length();
        int end = jsonBody.indexOf('"', start);
        return jsonBody.substring(start, end);
    }

    private String flipLastChar(String token) {
        char last = token.charAt(token.length() - 1);
        char replacement = last == 'a' ? 'b' : 'a';
        return token.substring(0, token.length() - 1) + replacement;
    }
}

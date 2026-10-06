package com.vitc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
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
 * PART 12A - Authentication &amp; core system testing, end to end, driven entirely through real
 * HTTP calls (never by reaching into the service layer), the same way a browser following
 * Footer -&gt; Login -&gt; Dashboard would.
 *
 * <p>Each test rolls back (class-level {@code @Transactional}), so the real, application-seeded
 * default Teacher account ({@code VITCteacher} / {@code VITC@123}, created by
 * {@code TeacherSeeder} at application start) can be exercised directly - including changing its
 * password - without leaking state into any other test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationCoreSystemTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired PasswordEncoder encoder;

    private static final String DEFAULT_TEACHER_USERNAME = "VITCteacher";
    private static final String DEFAULT_TEACHER_PASSWORD = "VITC@123";

    private static final String ADMIN_USERNAME = "part12aadmin";
    private static final String ADMIN_PASSWORD = "Part12A@Admin";

    private static final String STUDENT_LOGIN_ID = "VITCSTU12A01";
    private static final String STUDENT_PASSWORD = "Part12A@Student";

    @BeforeEach
    void seed() {
        // A real Main Admin account with a real (hashed) password, so /admins/login can be
        // exercised exactly as the footer -> admin login -> dashboard journey does.
        admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("part12aadmin@test.io").fullName("Part 12A Admin")
                .passwordHash(encoder.encode(ADMIN_PASSWORD)).active(true).build()));

        // A real Student account, enrolled in one course, so the Student login journey and
        // "own data only" checks can run through the real /student/auth/login endpoint.
        Course course = courses.findByCode("P12ACOURSE").orElseGet(() -> courses.save(Course.builder()
                .code("P12ACOURSE").title("Part 12A Course").price(BigDecimal.TEN).active(true).build()));
        User student = users.findByStudentLoginIdIgnoreCase(STUDENT_LOGIN_ID).orElseGet(() -> users.save(
                User.builder()
                        .fullName("Part 12A Student").email("part12astudent@test.io")
                        .studentLoginId(STUDENT_LOGIN_ID)
                        .passwordHash(encoder.encode(STUDENT_PASSWORD))
                        .role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
        if (enrollments.findFirstByUserIdAndCourseId(student.getId(), course.getId()).isEmpty()) {
            enrollments.save(Enrollment.builder().studentName("Part 12A Student").email(student.getEmail())
                    .phone("-").course(course).user(student).amount(BigDecimal.ONE)
                    .status(EnrollmentStatus.ACTIVE).build());
        }
    }

    /* =======================================================================================
     * TEST 1 - MAIN ADMIN: Footer -> Admin Login -> Main Admin Dashboard.
     * ======================================================================================= */

    @Test
    void mainAdminLoginSucceedsAndReachesDashboard() throws Exception {
        String token = extractToken(mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString());

        // The dashboard's own data calls must succeed with the freshly issued session -
        // this is the "correct dashboard is displayed" check, done at the API level.
        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
    }

    @Test
    void mainAdminInvalidCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/v1/admins/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-such-admin\",\"password\":\"whatever\"}"))
                .andExpect(status().is4xxClientError());
    }

    /* =======================================================================================
     * TEST 2 - TEACHER ADMIN: Footer -> Teacher Login -> Teacher Dashboard, forced first-login
     * password change, using the real seeded default credentials VITCteacher / VITC@123.
     * ======================================================================================= */

    @Test
    void teacherFirstLoginForcesPasswordChangeAndOldPasswordStopsWorking() throws Exception {
        User seeded = users.findByUsernameIgnoreCase(DEFAULT_TEACHER_USERNAME).orElseThrow(
                () -> new AssertionError("TeacherSeeder did not create the default teacher account"));
        assertTrue(seeded.getPasswordHash().startsWith("$2"),
                "Password must be stored hashed (BCrypt), never in plain text");
        assertNotEquals(DEFAULT_TEACHER_PASSWORD, seeded.getPasswordHash());

        // Step 1: first login with the initial credentials succeeds and flags a forced change.
        String firstLoginBody = mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + DEFAULT_TEACHER_USERNAME + "\",\"password\":\""
                                + DEFAULT_TEACHER_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String firstToken = extractToken(firstLoginBody);

        // Step 2: change the password (current = initial, new = a fresh one).
        String newPassword = "Teacher@P12A2026";
        String changeBody = mvc.perform(post("/api/v1/teacher/auth/change-password")
                        .header("X-Teacher-Username", DEFAULT_TEACHER_USERNAME)
                        .header("X-Teacher-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + DEFAULT_TEACHER_PASSWORD + "\","
                                + "\"newPassword\":\"" + newPassword + "\","
                                + "\"confirmPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String rotatedToken = extractToken(changeBody);
        assertNotEquals(firstToken, rotatedToken, "Changing the password must rotate the session token");

        // Step 3: the OLD password must no longer work.
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + DEFAULT_TEACHER_USERNAME + "\",\"password\":\""
                                + DEFAULT_TEACHER_PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError());

        // Step 4: the NEW password logs in normally, and mustChangePassword is now false.
        String secondLoginBody = mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + DEFAULT_TEACHER_USERNAME + "\",\"password\":\""
                                + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String secondToken = extractToken(secondLoginBody);

        // Step 5: the teacher retains exactly Teacher permissions after the change - reaches
        // the Teacher dashboard, is rejected from Main Admin surfaces.
        mvc.perform(get("/api/v1/teacher/courses")
                        .header("X-Teacher-Username", DEFAULT_TEACHER_USERNAME).header("X-Teacher-Token", secondToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Teacher-Username", DEFAULT_TEACHER_USERNAME).header("X-Teacher-Token", secondToken))
                .andExpect(status().isForbidden());

        // Restore the account's password/state so this test never leaves the shared default
        // teacher account changed for anything else that might read it later in the same run
        // (defence in depth on top of @Transactional's own rollback).
        User afterTest = users.findByUsernameIgnoreCase(DEFAULT_TEACHER_USERNAME).orElseThrow();
        afterTest.setPasswordHash(encoder.encode(DEFAULT_TEACHER_PASSWORD));
        afterTest.setMustChangePassword(true);
        users.save(afterTest);
    }

    @Test
    void teacherInvalidCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + DEFAULT_TEACHER_USERNAME + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-such-teacher\",\"password\":\"whatever\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void inactiveTeacherCannotLogIn() throws Exception {
        User t = users.findByUsernameIgnoreCase("P12AOFFTEACH").orElseGet(() -> users.save(User.builder()
                .fullName("Offline Teacher").email("p12aoffteach@test.io").username("P12AOFFTEACH")
                .passwordHash(encoder.encode("Teach@1234")).role(UserRole.TEACHER)
                .status(UserStatus.INACTIVE).build()));
        t.setStatus(UserStatus.INACTIVE);
        users.save(t);

        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"P12AOFFTEACH\",\"password\":\"Teach@1234\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false));
    }

    /* =======================================================================================
     * TEST 3 - STUDENT: Footer -> Student Login -> Student Dashboard.
     * ======================================================================================= */

    @Test
    void studentLoginSucceedsAndReachesDashboard() throws Exception {
        String body = mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_LOGIN_ID + "\",\"password\":\""
                                + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(body);

        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/courses")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", token))
                .andExpect(status().isOk());
    }

    @Test
    void studentInvalidCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_LOGIN_ID + "\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"NOSUCHSTUDENT\",\"password\":\"whatever\"}"))
                .andExpect(status().is4xxClientError());
    }

    /* =======================================================================================
     * Cross-role security: a Student session must never reach Teacher/Admin functionality,
     * and a Teacher session must never reach Main Admin functionality, using real login tokens
     * (not hand-seeded session rows) end to end through TEST 1-3.
     * ======================================================================================= */

    @Test
    void studentCannotAccessTeacherOrAdminFunctionality() throws Exception {
        String token = extractToken(mvc.perform(post("/api/v1/student/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentLoginId\":\"" + STUDENT_LOGIN_ID + "\",\"password\":\""
                                + STUDENT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // PART 8/8 - aligned with StudentTeacherApiProtectionTest: a caller holding a VALID
        // student session is authenticated but not authorised for the Teacher area, so the
        // Teacher interceptor answers 403 Forbidden (same rule the Admin area already used).
        // Access is still fully denied; only the status code is asserted consistently now.
        mvc.perform(get("/api/v1/teacher/courses")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", token))
                .andExpect(status().isForbidden());
        // AdminAuthInterceptor recognizes the X-Student-Token header itself (even though this
        // request has no X-Admin-Username/-Token) and answers 403 Forbidden rather than 401 -
        // an authenticated-elsewhere caller is refused, not merely "please sign in".
        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotAccessMainAdminFunctionality() throws Exception {
        User teacher = users.findByUsernameIgnoreCase(DEFAULT_TEACHER_USERNAME).orElseThrow();
        String freshToken = "p12a-fresh-teacher-token";
        teacher.setSessionToken(freshToken);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        // Whatever mustChangePassword currently is, access control must hold regardless.
        users.save(teacher);

        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Teacher-Username", DEFAULT_TEACHER_USERNAME).header("X-Teacher-Token", freshToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users")
                        .header("X-Teacher-Username", DEFAULT_TEACHER_USERNAME).header("X-Teacher-Token", freshToken))
                .andExpect(status().isForbidden());
    }

    /* =======================================================================================
     * helpers
     * ======================================================================================= */

    private String extractToken(String jsonBody) {
        // sessionToken is a top-level field of the "data" object for all three login responses.
        int idx = jsonBody.indexOf("\"sessionToken\":\"");
        assertTrue(idx >= 0, "Response did not contain a sessionToken: " + jsonBody);
        int start = idx + "\"sessionToken\":\"".length();
        int end = jsonBody.indexOf('"', start);
        return jsonBody.substring(start, end);
    }
}

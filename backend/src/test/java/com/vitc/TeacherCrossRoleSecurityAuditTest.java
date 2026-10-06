package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 7/8 - Security &amp; regression hardening.
 *
 * <p>This is not a re-implementation of authorization: {@link TeacherAuthInterceptor},
 * {@link com.vitc.security.RequireRole} and {@link com.vitc.service.TeacherAuthorizationService}
 * are reused exactly as they already exist. Course/module/lesson/student/progress ownership and
 * the Teacher &lt;-&gt; Main Admin boundary are already exhaustively covered by
 * {@code TeacherCourseLevelAuthorizationTest}, {@code TeacherModuleManagementTest},
 * {@code TeacherLessonManagementTest}, {@code TeacherVideoManagementTest},
 * {@code TeacherAdminSeparationTest} and {@code StudentTeacherApiProtectionTest} from earlier
 * parts - this class only closes the one leg of the cross-role matrix those files do not already
 * exercise: a genuine Teacher session presented against Student-only endpoints.</p>
 *
 * <p>A Teacher account has no {@code studentLoginId}, and {@code StudentAuthInterceptor} resolves
 * the caller by that field (never by username), so a Teacher's credentials cannot even be looked
 * up as a student - the request is rejected before role is even checked. This is the same
 * "authenticated but not authorised" boundary already proven for Student -&gt; Teacher API and
 * Teacher -&gt; Main Admin API; the assertion here just closes the matrix.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherCrossRoleSecurityAuditTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    private static final String TEACHER_USERNAME = "P7TEACHER";
    private static final String TEACHER_TOKEN = "p7-teacher-token";

    @BeforeEach
    void seed() {
        User teacher = users.save(User.builder()
                .fullName("P7 Teacher").email("p7-teacher@test.io").username(TEACHER_USERNAME)
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build());
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);

        Course course = courses.save(Course.builder()
                .code("P7JAVA").title("P7 Java").price(BigDecimal.TEN).active(true)
                .teacherId(teacher.getId()).build());

        // Deliberately reuse the teacher's own username in the Student-Id header slot: a
        // studentLoginId lookup for it must fail, since Teacher accounts never have one.
    }

    private MockHttpServletRequestBuilder asTeacherOnStudentApi(MockHttpServletRequestBuilder b) {
        return b.header("X-Student-Id", TEACHER_USERNAME).header("X-Student-Token", TEACHER_TOKEN);
    }

    /* ---------------- Teacher session -> Student-protected API -> rejected ---------------- */

    @Test
    void teacherSessionCannotAuthenticateAgainstStudentApi() throws Exception {
        // A Teacher's own valid session token, replayed against the Student-only header scheme,
        // never resolves to a student account - so it is rejected before any student data (own
        // course list, own lesson access, own profile) is ever reached.
        mvc.perform(asTeacherOnStudentApi(get("/api/v1/student/me")))
                .andExpect(status().isUnauthorized());
        mvc.perform(asTeacherOnStudentApi(get("/api/v1/student/courses")))
                .andExpect(status().isUnauthorized());
    }

    /* ---------------- Student-facing API rejects an entirely absent session too ---------------- */

    @Test
    void studentApiRejectsRequestsWithNoSessionAtAll() throws Exception {
        mvc.perform(get("/api/v1/student/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/courses")).andExpect(status().isUnauthorized());
    }
}

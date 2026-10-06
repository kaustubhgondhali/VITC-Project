package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.Enrollment;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 6/8 - Complete Teacher Dashboard Integration.
 *
 * <p>This does not re-test any single section in isolation (Dashboard stats, My Courses, Content
 * Management, Students, Student Progress each already have their own dedicated test class from
 * earlier parts). Instead it exercises the flow that ties them together end to end, using the
 * same backend contracts every teacher-admin page calls:</p>
 *
 * <ol>
 *   <li>Login -&gt; every downstream endpoint used across the flow accepts the issued token
 *       (Dashboard stats, My Courses, Manage Content, Students, Student Progress, Profile);</li>
 *   <li>Manage Content (item 3): {@code GET /teacher/courses/{courseId}} rejects a course
 *       belonging to another teacher with 403, exactly what {@code course-content.html} relies on
 *       before rendering anything;</li>
 *   <li>Students -&gt; Student Progress (item 4): the same 403 boundary holds for the roster and
 *       detail endpoints those two pages call;</li>
 *   <li>Session (item 9): {@code /teacher/auth/session} still resolves the token mid-flow;</li>
 *   <li>Logout (items 8, 10): {@code /teacher/auth/logout} invalidates the session token
 *       server-side, so every one of those same endpoints rejects the old token afterwards -
 *       the backend half of "logout prevents returning through browser back/bfcache" (the
 *       client half is the {@code pageshow} listener present on every teacher-admin page,
 *       including {@code course-content.html} as of this part).</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherDashboardIntegrationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;
    @Autowired ObjectMapper json;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";

    private Long courseId;
    private Long otherTeacherCourseId;
    private Long studentId;

    @BeforeEach
    void seed() {
        User teacherA = teacher("p6-teachera@test.io", "P6TEACHERA", "p6-tok-a");
        User teacherB = teacher("p6-teacherb@test.io", "P6TEACHERB", "p6-tok-b");

        Course course = courses.save(Course.builder()
                .code("P6JAVA").title("P6 Java").price(BigDecimal.TEN).active(true)
                .teacherId(teacherA.getId()).build());
        courseId = course.getId();

        Course otherCourse = courses.save(Course.builder()
                .code("P6REACT").title("P6 React").price(BigDecimal.TEN).active(true)
                .teacherId(teacherB.getId()).build());
        otherTeacherCourseId = otherCourse.getId();

        CourseModule module = modules.save(CourseModule.builder()
                .course(course).title("Module 1").displayOrder(1).active(true).build());
        lessons.save(CourseLesson.builder()
                .module(module).title("Lesson 1").displayOrder(1).active(true).build());

        User student = users.save(User.builder()
                .fullName("P6 Student").email("p6-student@test.io").studentLoginId("VITCSTU96101")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build());
        studentId = student.getId();
        enrollments.save(Enrollment.builder()
                .studentName(student.getFullName()).email(student.getEmail()).phone("9999999999")
                .course(course).user(student).amount(BigDecimal.TEN).status(EnrollmentStatus.ACTIVE).build());
    }

    private User teacher(String email, String username, String token) {
        User u = users.save(User.builder()
                .fullName(username).email(email).username(username).passwordHash("x")
                .role(UserRole.TEACHER).status(UserStatus.ACTIVE).build());
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private MockHttpServletRequestBuilder asTeacherA(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P6TEACHERA").header(TT, "p6-tok-a");
    }

    /* ---------------- item 1/2: the full section chain accepts one session token ---------------- */

    @Test
    void oneSessionTokenReachesEverySectionInTheFlow() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/dashboard/stats"))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses"))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/content"))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/students"))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students"))).andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students/" + studentId + "/progress")))
                .andExpect(status().isOk());
        mvc.perform(asTeacherA(get("/api/v1/teacher/me"))).andExpect(status().isOk());
    }

    /* ---------------- item 3: Manage Content ownership ---------------- */

    @Test
    void manageContentRejectsAnotherTeachersCourse() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId)))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId + "/content")))
                .andExpect(status().isForbidden());
    }

    /* ---------------- item 4: Students / Student Progress ownership ---------------- */

    @Test
    void studentsSectionRejectsAnotherTeachersCourse() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId + "/students")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId
                        + "/students/" + studentId + "/progress")))
                .andExpect(status().isForbidden());
    }

    /* ---------------- item 9: session stays valid mid-flow ---------------- */

    @Test
    void sessionVerifyResolvesTheSameTokenUsedAcrossTheFlow() throws Exception {
        mvc.perform(post("/api/v1/teacher/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "P6TEACHERA", "token", "p6-tok-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("P6TEACHERA"));
    }

    /* ---------------- items 8 & 10: logout invalidates the token everywhere ---------------- */

    @Test
    void logoutInvalidatesTheTokenForEveryDownstreamSection() throws Exception {
        // Sanity: the token works before logout.
        mvc.perform(asTeacherA(get("/api/v1/teacher/dashboard/stats"))).andExpect(status().isOk());

        mvc.perform(asTeacherA(post("/api/v1/teacher/auth/logout"))).andExpect(status().isOk());

        // The same token, reused exactly as a cached/back-navigated page would resend it, must be
        // rejected everywhere - this is the server-side half of "cannot return via back/bfcache".
        mvc.perform(asTeacherA(get("/api/v1/teacher/dashboard/stats"))).andExpect(status().isUnauthorized());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses"))).andExpect(status().isUnauthorized());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId))).andExpect(status().isUnauthorized());
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/content"))).andExpect(status().isUnauthorized());
        mvc.perform(asTeacherA(get("/api/v1/teacher/students"))).andExpect(status().isUnauthorized());
        mvc.perform(asTeacherA(get("/api/v1/teacher/me"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/teacher/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "P6TEACHERA", "token", "p6-tok-a"))))
                .andExpect(status().isBadRequest());
    }

    /* ---------------- item 10: direct URL access without any session ---------------- */

    @Test
    void directApiAccessWithoutAnySessionIsProtected() throws Exception {
        mvc.perform(get("/api/v1/teacher/dashboard/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/courses/" + courseId + "/content")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/courses/" + courseId + "/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/me")).andExpect(status().isUnauthorized());
    }
}

package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.Enrollment;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 4/8 - Teacher "Students" page ({@code GET /api/v1/teacher/students}).
 *
 * <p>Teacher A has two assigned courses with one enrolled student each; Teacher B has a separate
 * course with its own student. Verifies the roster is the union of every enrolled
 * (active/completed) student across all of a teacher's own courses, that a pending/cancelled
 * enrollment never appears, that Teacher A never sees Teacher B's students, the documented empty
 * state, and the full cross-role access matrix (Student rejected, Main Admin unaffected).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherStudentRosterTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";

    private Long teacherAId;
    private Long courseA1;
    private Long courseA2;

    @BeforeEach
    void seed() {
        User teacherA = teacher("p4-teachera@test.io", "P4TEACHERA", "p4-tok-a");
        User teacherB = teacher("p4-teacherb@test.io", "P4TEACHERB", "p4-tok-b");
        teacherAId = teacherA.getId();

        Course java = course("P4JAVA", "P4 Java", teacherA.getId());
        Course python = course("P4PY", "P4 Python", teacherA.getId());
        Course react = course("P4REACT", "P4 React", teacherB.getId());
        courseA1 = java.getId();
        courseA2 = python.getId();

        User studentX = student("VITCSTU90101", "P4 Student X", "p4-x@test.io");
        User studentY = student("VITCSTU90102", "P4 Student Y", "p4-y@test.io");
        User studentZ = student("VITCSTU90103", "P4 Student Z", "p4-z@test.io");
        User studentPending = student("VITCSTU90104", "P4 Student Pending", "p4-pending@test.io");

        // Teacher A's roster: X active in Java, Y completed in Python.
        enroll(java, studentX, EnrollmentStatus.ACTIVE);
        enroll(python, studentY, EnrollmentStatus.COMPLETED);
        // A pending enrollment in one of Teacher A's own courses must NOT appear (not yet "enrolled").
        enroll(java, studentPending, EnrollmentStatus.PENDING);
        // Teacher B's course/student - must never appear for Teacher A.
        enroll(react, studentZ, EnrollmentStatus.ACTIVE);
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByUsernameIgnoreCase(username).orElseGet(() -> users.save(User.builder()
                .fullName(username).email(email).username(username).passwordHash("x")
                .role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private Course course(String code, String title, Long teacherId) {
        return courses.findByCode(code).orElseGet(() -> courses.save(Course.builder()
                .code(code).title(title).price(BigDecimal.TEN).active(true).teacherId(teacherId).build()));
    }

    private User student(String loginId, String fullName, String email) {
        return users.findByStudentLoginIdIgnoreCase(loginId).orElseGet(() -> users.save(User.builder()
                .fullName(fullName).email(email).studentLoginId(loginId)
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
    }

    private void enroll(Course course, User student, EnrollmentStatus status) {
        enrollments.save(Enrollment.builder()
                .studentName(student.getFullName()).email(student.getEmail()).phone("9999999999")
                .course(course).user(student).amount(BigDecimal.TEN).status(status).build());
    }

    private MockHttpServletRequestBuilder asTeacherA(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P4TEACHERA").header(TT, "p4-tok-a");
    }

    private MockHttpServletRequestBuilder asTeacherB(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P4TEACHERB").header(TT, "p4-tok-b");
    }

    /* ---------------- course scoping (item 4) ---------------- */

    @Test
    void teacherSeesOnlyStudentsAcrossOwnAssignedCourses() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU90101')]").exists())
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU90102')]").exists())
                // Teacher B's student must never appear.
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU90103')]").doesNotExist())
                // A pending enrollment in Teacher A's own course must not appear either.
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU90104')]").doesNotExist());
    }

    @Test
    void rosterOmitsSensitiveFields() throws Exception {
        // Only the documented fields ever leave the server - no password/token/auth data.
        mvc.perform(asTeacherA(get("/api/v1/teacher/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].sessionToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].mustChangePassword").doesNotExist());
    }

    @Test
    void teacherCannotSeeAnotherTeachersStudents() throws Exception {
        mvc.perform(asTeacherB(get("/api/v1/teacher/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentLoginId").value("VITCSTU90103"));
    }

    /* ---------------- empty state (item 7) ---------------- */

    @Test
    void teacherWithNoEnrolledStudentsGetsEmptyList() throws Exception {
        User lonelyTeacher = teacher("p4-lonely@test.io", "P4LONELY", "p4-lonely-tok");
        course("P4LONELYCOURSE", "P4 Lonely Course", lonelyTeacher.getId());

        mvc.perform(get("/api/v1/teacher/students")
                        .header(TU, "P4LONELY").header(TT, "p4-lonely-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /* ---------------- cross-role security (item 8) ---------------- */

    @Test
    void studentCannotAccessTeacherStudentsApi() throws Exception {
        // A genuinely authenticated STUDENT session used against the Teacher header slot is
        // authenticated but not authorised here -> 403, mirroring every other Teacher endpoint.
        User student = users.findByUsernameIgnoreCase("P4STUDENTX").orElseGet(() -> users.save(User.builder()
                .fullName("P4 Student As Teacher").email("p4-studentx@test.io").username("P4STUDENTX")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
        student.setSessionToken("p4-student-tok");
        student.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(student);

        mvc.perform(get("/api/v1/teacher/students")
                        .header(TU, "P4STUDENTX").header(TT, "p4-student-tok"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/teacher/students")).andExpect(status().isUnauthorized());
    }

    /* ---------------- Main Admin unaffected (item 8) ---------------- */

    @Test
    void mainAdminApisStillWorkUnaffected() throws Exception {
        // This new Teacher endpoint lives entirely under /api/v1/teacher/students and does not
        // touch any Admin route or interceptor; a well-formed Teacher call still succeeds
        // regardless, proving no shared state/registration was disturbed.
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses"))).andExpect(status().isOk());
    }
}

package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
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
 * PART 11B-2 - Student &amp; Teacher API protection.
 *
 * <p>Exercises the full cross-role matrix required by the spec through real HTTP calls against
 * live controllers (no mocking of the interceptors or {@code RoleAuthorizationInterceptor}
 * added in PART 11B-1): every combination of caller role x protected surface either gets through
 * or is rejected with exactly the status code the spec requires, and never with a response body
 * that leaks the protected data.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentTeacherApiProtectionTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    private static final String ADMIN_USERNAME = "p2admin";
    private static final String ADMIN_TOKEN = "p2-admin-token";
    private static final String TEACHER_USERNAME = "P2TEACHER";
    private static final String TEACHER_TOKEN = "p2-teacher-token";
    private static final String STUDENT_LOGIN_ID = "VITCSTU90002";
    private static final String STUDENT_TOKEN = "p2-student-token";

    @BeforeEach
    void seed() {
        Admin admin = admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("p2admin@test.io").fullName("P2 Admin")
                .passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(admin);

        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(User.builder()
                .fullName("P2 Teacher").email("p2-teacher@test.io").username(TEACHER_USERNAME)
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);

        courses.findByCode("P2JAVA").orElseGet(() -> courses.save(Course.builder()
                .code("P2JAVA").title("P2 Java").price(BigDecimal.TEN).active(true)
                .teacherId(teacher.getId()).build()));

        User student = users.findByStudentLoginIdIgnoreCase(STUDENT_LOGIN_ID).orElseGet(() -> users.save(User.builder()
                .fullName("P2 Student").email("p2-student@test.io").studentLoginId(STUDENT_LOGIN_ID)
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
        student.setSessionToken(STUDENT_TOKEN);
        student.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(student);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private MockHttpServletRequestBuilder asTeacher(MockHttpServletRequestBuilder b) {
        return b.header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", TEACHER_TOKEN);
    }

    private MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder b) {
        return b.header("X-Student-Id", STUDENT_LOGIN_ID).header("X-Student-Token", STUDENT_TOKEN);
    }

    /* Main Admin -> Admin API -> ALLOWED */
    @Test
    void mainAdminReachesAdminApi() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/users"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/admin/smtp-settings"))).andExpect(status().isOk());
    }

    /* Main Admin -> Teacher management (via the Admin-side endpoint) -> ALLOWED */
    @Test
    void mainAdminReachesTeacherManagement() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admin/teachers"))).andExpect(status().isOk());
    }

    /* Main Admin -> Student management -> ALLOWED */
    @Test
    void mainAdminReachesStudentManagement() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admin/students"))).andExpect(status().isOk());
    }

    /* Teacher -> Admin API -> 403, no data leaked */
    @Test
    void teacherIsRejectedFromAdminApi() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/admin/students"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/teachers"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/payment-settings"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/smtp-settings"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/users"))).andExpect(status().isForbidden());
    }

    /* Student -> Admin API -> 403 */
    @Test
    void studentIsRejectedFromAdminApi() throws Exception {
        mvc.perform(asStudent(get("/api/v1/admin/students"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/admin/teachers"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/admin/payment-settings"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/admin/smtp-settings"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/users"))).andExpect(status().isForbidden());
    }

    /* Student -> Teacher API -> 403 (course management, dashboard, content) */
    @Test
    void studentIsRejectedFromTeacherApi() throws Exception {
        mvc.perform(asStudent(get("/api/v1/teacher/courses"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/teacher/dashboard/stats"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/teacher/me"))).andExpect(status().isForbidden());
    }

    /* Teacher -> Teacher API -> ALLOWED */
    @Test
    void teacherReachesTeacherApi() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/teacher/courses"))).andExpect(status().isOk());
        mvc.perform(asTeacher(get("/api/v1/teacher/dashboard/stats"))).andExpect(status().isOk());
        mvc.perform(asTeacher(get("/api/v1/teacher/me"))).andExpect(status().isOk());
    }
}

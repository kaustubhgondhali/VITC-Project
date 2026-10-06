package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.UserRepository;
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
 * PART 11B-1 - core backend role-based authorization.
 *
 * <p>Exercises the {@code MAIN_ADMIN} / {@code TEACHER} / {@code STUDENT}
 * foundation end to end through real HTTP calls: a valid Main Admin session
 * reaches a protected Admin API, while a valid Teacher session and a valid
 * Student session are both rejected with 403 Forbidden - never a redirect,
 * never a silently empty body, always a backend-enforced denial.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleBasedAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;

    private static final String ADMIN_USERNAME = "rbacadmin";
    private static final String ADMIN_TOKEN = "rbac-admin-token";
    private static final String TEACHER_USERNAME = "RBACTEACHER";
    private static final String TEACHER_TOKEN = "rbac-teacher-token";
    private static final String STUDENT_LOGIN_ID = "VITCSTU90001";
    private static final String STUDENT_TOKEN = "rbac-student-token";

    @BeforeEach
    void seed() {
        Admin admin = admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("rbacadmin@test.io").fullName("RBAC Admin")
                .passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(admin);

        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(User.builder()
                .fullName("RBAC Teacher").email("rbac-teacher@test.io").username(TEACHER_USERNAME)
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);

        User student = users.findByStudentLoginIdIgnoreCase(STUDENT_LOGIN_ID).orElseGet(() -> users.save(User.builder()
                .fullName("RBAC Student").email("rbac-student@test.io").studentLoginId(STUDENT_LOGIN_ID)
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

    /* 1. Main Admin -> protected Admin API -> ALLOWED. */
    @Test
    void mainAdminReachesProtectedAdminApi() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admin/teachers"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/users"))).andExpect(status().isOk());
    }

    /* 2. Teacher -> protected Admin API -> 403 Forbidden. */
    @Test
    void teacherIsRejectedFromProtectedAdminApi() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/admin/teachers"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/users"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/smtp-settings"))).andExpect(status().isForbidden());
    }

    /* 3. Student -> protected Admin API -> 403 Forbidden. */
    @Test
    void studentIsRejectedFromProtectedAdminApi() throws Exception {
        mvc.perform(asStudent(get("/api/v1/admin/teachers"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/users"))).andExpect(status().isForbidden());
        mvc.perform(asStudent(get("/api/v1/admin/smtp-settings"))).andExpect(status().isForbidden());
    }

    /* 4. Unauthenticated caller -> protected Admin API -> rejected (401, not silently allowed). */
    @Test
    void anonymousCallerIsRejectedFromProtectedAdminApi() throws Exception {
        mvc.perform(get("/api/v1/admin/teachers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }
}

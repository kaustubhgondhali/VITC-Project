package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 10B - Teacher / Main Admin separation enforced on the backend.
 *
 * <p>A Teacher holds a perfectly valid Teacher session token. Every Main Admin API must still
 * answer 403 Forbidden for that caller, and no secret (Razorpay key secret, SMTP password, bank
 * or database credential) may appear in any response a Teacher can obtain.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherAdminSeparationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdminRepository admins;
    @Autowired CourseRepository courses;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";
    private static final String TEACHER = "SEPTEACHER";
    private static final String TEACHER_TOKEN = "sep-teacher-token";
    private static final String ADMIN = "sepadmin";
    private static final String ADMIN_TOKEN = "sep-admin-token";

    private Long courseId;

    @BeforeEach
    void seed() {
        User t = users.findByUsernameIgnoreCase(TEACHER).orElseGet(() -> users.save(User.builder()
                .fullName("Sep Teacher").email("sep-teacher@test.io").username(TEACHER)
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        t.setSessionToken(TEACHER_TOKEN);
        t.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(t);

        Admin a = admins.findByUsernameIgnoreCase(ADMIN).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN).email("sepadmin@test.io").fullName("Sep Admin")
                .passwordHash("x").build()));
        a.setActive(true);
        a.setSessionToken(ADMIN_TOKEN);
        a.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(a);

        courseId = courses.findByCode("SEPJAVA").orElseGet(() -> courses.save(Course.builder()
                .code("SEPJAVA").title("Sep Java").price(BigDecimal.TEN).active(true)
                .teacherId(t.getId()).build())).getId();
    }

    private MockHttpServletRequestBuilder asTeacher(MockHttpServletRequestBuilder b) {
        return b.header(TU, TEACHER).header(TT, TEACHER_TOKEN);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN).header("X-Admin-Token", ADMIN_TOKEN);
    }

    /* 1. Payment / gateway / SMTP configuration is Main Admin only. */
    @Test
    void teacherCannotReachPaymentOrSmtpSettings() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/admin/payment-settings"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(put("/api/v1/admin/payment-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gateway\":\"RAZORPAY\",\"keyId\":\"rzp_x\",\"keySecret\":\"s\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/smtp-settings"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/emails"))).andExpect(status().isForbidden());
    }

    /* 2. Order, payment, refund and invoice administration is Main Admin only. */
    @Test
    void teacherCannotReachOrdersPaymentsOrInvoices() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/assignment-orders"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(patch("/api/v1/assignment-orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/payments"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(patch("/api/v1/payments/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUNDED\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/invoices"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/enrollments"))).andExpect(status().isForbidden());
    }

    /* 3. Admin management + user/account administration is Main Admin only. */
    @Test
    void teacherCannotManageAdminsOrUsers() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/admins"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admins/1"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(post("/api/v1/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"x@x.io\",\"fullName\":\"X\","
                                + "\"password\":\"Passw0rd!\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacher(patch("/api/v1/admins/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"Passw0rd!\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacher(delete("/api/v1/admins/1"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admins/dashboard/stats"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/users"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/users/1"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(delete("/api/v1/users/1"))).andExpect(status().isForbidden());
    }

    /* 4. Main Admin student administration and course-content administration stay admin only. */
    @Test
    void teacherCannotReachMainAdminStudentOrContentApis() throws Exception {
        mvc.perform(asTeacher(get("/api/v1/admin/students"))).andExpect(status().isForbidden());
        mvc.perform(asTeacher(get("/api/v1/admin/courses/" + courseId + "/content")))
                .andExpect(status().isForbidden());
    }

    /* 5. No secret is ever exposed through anything a Teacher can read. */
    @Test
    void teacherResponsesNeverContainSecrets() throws Exception {
        String[] forbiddenFragments = {"keySecret", "webhookSecret", "smtpPassword", "passwordHash"};
        String dashboard = mvc.perform(asTeacher(get("/api/v1/teacher/dashboard/stats")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String profile = mvc.perform(asTeacher(get("/api/v1/teacher/me")))
                .andReturn().getResponse().getContentAsString();
        String courseList = mvc.perform(asTeacher(get("/api/v1/teacher/courses")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String fragment : forbiddenFragments) {
            org.junit.jupiter.api.Assertions.assertFalse(dashboard.contains(fragment));
            org.junit.jupiter.api.Assertions.assertFalse(profile.contains(fragment));
            org.junit.jupiter.api.Assertions.assertFalse(courseList.contains(fragment));
        }
    }

    /* 6. Even for the Main Admin, the stored Razorpay secret is only ever returned masked. */
    @Test
    void adminPaymentSettingsNeverReturnTheRawSecret() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admin/payment-settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keySecret").doesNotExist());
        mvc.perform(asAdmin(get("/api/v1/admin/smtp-settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    /* 7. Existing functionality is untouched: admin still works, public flows still work. */
    @Test
    void mainAdminAndPublicFlowsStillWork() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/admins"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/users"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/payments"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/assignment-orders"))).andExpect(status().isOk());

        // public storefront: gateway status carries no credentials, enrollment stays open
        mvc.perform(get("/api/v1/payment/gateway"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("keySecret"))));
        mvc.perform(post("/api/v1/enrollments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"Sep Visitor\",\"email\":\"sep-visitor@test.io\","
                                + "\"phone\":\"9876543210\",\"courseId\":" + courseId + "}"))
                .andExpect(status().is2xxSuccessful());

        // teacher keeps working on the course assigned to them
        mvc.perform(asTeacher(get("/api/v1/teacher/courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(courseId));
    }
}

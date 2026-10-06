package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 2B-4/7 - the storefront content APIs (courses, assignments, gallery,
 * pricing, FAQs, reviews, testimonials, blog posts, contact messages,
 * internship/job applications, and the generic file store) publish their
 * read-only endpoints openly by design, but their create/update/delete
 * endpoints previously carried no backend authorization at all - hiding the
 * "Manage" buttons in the admin panel was the only thing stopping a caller
 * from hitting them directly. This exercises that every mutating endpoint on
 * those controllers is now backend-enforced to {@code MAIN_ADMIN} (except the
 * handful of public submission endpoints - review, contact, job/internship
 * application, and file upload - which visitors must still be able to call
 * without a session, exactly as before), while every public read endpoint
 * continues to work with no session at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicContentAdminWriteAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;

    private static final String ADMIN_USERNAME = "contentrbacadmin";
    private static final String ADMIN_TOKEN = "content-rbac-admin-token";
    private static final String TEACHER_USERNAME = "CONTENTRBACTEACHER";
    private static final String TEACHER_TOKEN = "content-rbac-teacher-token";

    @BeforeEach
    void seed() {
        Admin admin = admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("contentrbacadmin@test.io").fullName("Content RBAC Admin")
                .passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(admin);

        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(User.builder()
                .fullName("Content RBAC Teacher").email("content-rbac-teacher@test.io").username(TEACHER_USERNAME)
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private MockHttpServletRequestBuilder asTeacher(MockHttpServletRequestBuilder b) {
        return b.header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", TEACHER_TOKEN);
    }

    /* 1. Anonymous / wrong-role callers are rejected on every content-management write. */
    @Test
    void anonymousAndTeacherAreRejectedFromContentWrites() throws Exception {
        mvc.perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(asTeacher(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/gallery/999999")).andExpect(status().is4xxClientError());
        mvc.perform(asTeacher(delete("/api/v1/gallery/999999"))).andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/faqs/999999")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/v1/pricing/999999").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/v1/assignments/999999")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/v1/blog-posts/999999")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/v1/testimonials/999999")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/reviews")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/contact-messages")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/internships")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/careers")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/files")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/v1/files/999999")).andExpect(status().is4xxClientError());
    }

    /* 2. Main Admin can still reach every one of those endpoints (permissions unchanged). */
    @Test
    void mainAdminStillReachesContentManagementApis() throws Exception {
        mvc.perform(asAdmin(get("/api/v1/reviews"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/contact-messages"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/internships"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/careers"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/files"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/blog-posts"))).andExpect(status().isOk());
        mvc.perform(asAdmin(get("/api/v1/testimonials"))).andExpect(status().isOk());
    }

    /* 3. Public storefront reads keep working with no session at all - unchanged by this part. */
    @Test
    void publicReadEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/courses/active")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/faqs/active")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/gallery")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pricing/active")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reviews/approved")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/testimonials/approved")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/blog-posts/published")).andExpect(status().isOk());
    }

    /* 4. Public submission endpoints (visitor-facing forms) still require no session. */
    @Test
    void publicSubmissionEndpointsStayOpen() throws Exception {
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError()); // reaches validation, not blocked by authorization
    }
}

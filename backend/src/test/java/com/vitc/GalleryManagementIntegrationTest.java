package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GalleryManagementIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;

    private static final String ADMIN_USERNAME = "galleryrbacadmin";
    private static final String ADMIN_TOKEN = "gallery-rbac-admin-token";
    private static final String TEACHER_USERNAME = "GALLERYRBACTEACHER";
    private static final String TEACHER_TOKEN = "gallery-rbac-teacher-token";

    @BeforeEach
    void seed() {
        Admin admin = admins.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN_USERNAME).email("galleryrbacadmin@test.io").fullName("Gallery RBAC Admin")
                .passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(admin);

        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(User.builder()
                .fullName("Gallery RBAC Teacher").email("gallery-rbac-teacher@test.io")
                .username(TEACHER_USERNAME).passwordHash("x").role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE).build()));
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private MockHttpServletRequestBuilder asTeacher(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", TEACHER_TOKEN);
    }

    @Test
    void mainAdminCanCreateGalleryCategoryAndPublicUsersCanReadIt() throws Exception {
        mvc.perform(asAdmin(post("/api/v1/gallery/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Annual Function\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("Annual Function"));

        mvc.perform(get("/api/v1/gallery/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mvc.perform(asTeacher(post("/api/v1/gallery/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Teacher Only\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mainAdminCanUploadGalleryImageUsingFileMultipartPart() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "gallery-test.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        mvc.perform(asAdmin(multipart("/api/v1/files/gallery").file(image)
                        .param("uploadedBy", ADMIN_USERNAME)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/uploads/gallery/")));
    }
}

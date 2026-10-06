package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 7/8 - TEST 9 (LARGE FILE).
 *
 * <p>{@code app.media.max-file-size} is lowered to a tiny value just for this test class so the
 * "too large" branch of {@code FileStorageServiceImpl.uploadVideo} can be exercised with a small
 * in-memory payload instead of actually uploading hundreds of megabytes. This never touches the
 * real 500MB default used everywhere else (see {@code application.properties} /
 * {@code app.media.max-file-size}) - the override only applies inside this test class's Spring
 * context.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.media.max-file-size=1KB")
@Transactional
class TeacherVideoUploadSizeLimitTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";

    private Long moduleA;

    @BeforeEach
    void seed() {
        User a = users.findByUsernameIgnoreCase("SIZETEACHERA").orElseGet(() -> users.save(User.builder()
                .fullName("SIZETEACHERA").email("size-a@test.io").username("SIZETEACHERA")
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        a.setSessionToken("size-tok-a");
        a.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(a);

        Course ca = courses.findByCode("SIZEA").orElseGet(() -> courses.save(Course.builder()
                .code("SIZEA").title("Video Size Limit Test").price(BigDecimal.TEN)
                .active(true).teacherId(a.getId()).build()));
        moduleA = modules.save(CourseModule.builder()
                .course(ca).title("Size Limit Module").displayOrder(1).active(true).build()).getId();
    }

    private Long addLesson(String title) throws Exception {
        String body = mvc.perform(post("/api/v1/teacher/modules/" + moduleA + "/lessons")
                        .header(U, "SIZETEACHERA").header(T, "size-tok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    /** TEST 9: a file above the configured limit is rejected and nothing is stored or persisted. */
    @Test
    void uploadAboveConfiguredLimitIsRejected() throws Exception {
        Long lessonId = addLesson("Oversized upload");

        // app.media.max-file-size is 1KB for this test class; 4KB is comfortably over that, but
        // still tiny in absolute terms so the test stays fast and memory-light.
        byte[] tooLarge = new byte[4096];
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "huge.mp4", "video/mp4", tooLarge);

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video")
                        .file(oversized)
                        .param("duration", "10:00")
                        .header(U, "SIZETEACHERA").header(T, "size-tok-a"))
                .andExpect(status().isBadRequest());

        // Nothing was persisted onto the lesson, and nothing playable was created for it.
        mvc.perform(get("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "SIZETEACHERA").header(T, "size-tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());
    }

    /** A file at/under the configured limit still succeeds - the check is a ceiling, not a trap. */
    @Test
    void uploadAtOrUnderConfiguredLimitStillSucceeds() throws Exception {
        Long lessonId = addLesson("Within limit upload");

        // PART 2/10: the payload carries a real ISO-BMFF ("ftyp") header, because video uploads
        // are now validated against the actual container bytes and not just the extension.
        byte[] withinLimit = new byte[512]; // well under the 1KB test ceiling
        System.arraycopy(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'},
                0, withinLimit, 0, 12);
        MockMultipartFile ok = new MockMultipartFile("file", "small.mp4", "video/mp4", withinLimit);

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video")
                        .file(ok)
                        .param("duration", "01:00")
                        .header(U, "SIZETEACHERA").header(T, "size-tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl")
                        .value(org.hamcrest.Matchers.containsString("/uploads/videos/")));
    }
}

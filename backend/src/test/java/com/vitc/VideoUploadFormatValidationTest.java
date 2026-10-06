package com.vitc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 2/10 — VIDEO UPLOAD.
 *
 * <p>Covers the full upload contract of {@code PUT /api/v1/teacher/lessons/{id}/video}:</p>
 * <ul>
 *   <li>every supported container is accepted (MP4, WebM, MOV, AVI, MKV, MPEG, MPG, M4V, 3GP,
 *       FLV, OGV), including when the browser declares no type or {@code application/octet-stream};</li>
 *   <li>awkward file names (spaces, parentheses, unicode, very long, traversal attempts) are
 *       accepted but never used to build the stored path;</li>
 *   <li>an unsupported format, a mismatched container and a non-video file are rejected with a
 *       meaningful message and leave the lesson untouched;</li>
 *   <li>the video lands on the correct course → module → lesson and nowhere else;</li>
 *   <li>the response never leaks a filesystem path, token or secret.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VideoUploadFormatValidationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";

    private Long moduleA;
    private Long otherTeacherLesson;

    @BeforeEach
    void seed() {
        User a = teacher("fmt-a@test.io", "FMTTEACHERA", "fmt-tok-a");
        User b = teacher("fmt-b@test.io", "FMTTEACHERB", "fmt-tok-b");
        Course ca = course("FMTA", "Format Test Java", a.getId());
        Course cb = course("FMTB", "Format Test Python", b.getId());
        moduleA = modules.save(CourseModule.builder()
                .course(ca).title("Module A").displayOrder(1).active(true).build()).getId();
        CourseModule mb = modules.save(CourseModule.builder()
                .course(cb).title("Module B").displayOrder(1).active(true).build());
        otherTeacherLesson = lessons.save(CourseLesson.builder()
                .module(mb).title("Foreign lesson").displayOrder(1).active(true).build()).getId();
    }

    /* ------------------------------------------------------------------ fixtures */

    private static byte[] pad(byte[] head) {
        byte[] out = new byte[Math.max(256, head.length)];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    /** Minimal but genuine container headers — the backend inspects these bytes. */
    private static byte[] payloadFor(String ext) {
        return switch (ext) {
            case "mp4", "m4v", "3gp", "mov" -> pad(new byte[] {
                0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
            case "webm", "mkv" -> pad(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4, 5, 6, 7, 8});
            case "avi" -> pad(new byte[] {'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'A', 'V', 'I', ' '});
            case "mpeg", "mpg" -> pad(new byte[] {0x00, 0x00, 0x01, (byte) 0xBA, 1, 2, 3, 4, 5, 6, 7, 8});
            case "flv" -> pad(new byte[] {'F', 'L', 'V', 0x01, 0x05, 0, 0, 0, 9, 0, 0, 0});
            case "ogv" -> pad(new byte[] {'O', 'g', 'g', 'S', 0, 2, 0, 0, 0, 0, 0, 0});
            default -> pad("not a video at all".getBytes(StandardCharsets.UTF_8));
        };
    }

    private static MockMultipartFile video(String name, String declaredType) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String ext = lower.substring(lower.lastIndexOf('.') + 1);
        return new MockMultipartFile("file", name, declaredType, payloadFor(ext));
    }

    private static MockMultipartHttpServletRequestBuilder putVideo(Long lessonId) {
        return multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video");
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

    private Long addLesson(String title) throws Exception {
        String body = mvc.perform(post("/api/v1/teacher/modules/" + moduleA + "/lessons")
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String upload(Long lessonId, MockMultipartFile file) throws Exception {
        return mvc.perform(putVideo(lessonId).file(file)
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /* ------------------------------------------------------------------ formats */

    /** TEST 1: every supported container is accepted, whatever the browser declared. */
    @Test
    void everySupportedVideoFormatIsAccepted() throws Exception {
        String[][] cases = {
            {"lesson.mp4", "video/mp4"},
            {"lesson.webm", "video/webm"},
            {"lesson.mov", "video/quicktime"},
            {"lesson.avi", "video/x-msvideo"},
            {"lesson.mkv", "application/octet-stream"},   // browsers often send this for MKV
            {"lesson.mpeg", "video/mpeg"},
            {"lesson.mpg", ""},                            // ...or nothing at all
            {"lesson.m4v", "video/x-m4v"},
            {"lesson.3gp", "video/3gpp"},
            {"lesson.flv", "video/x-flv"},
            {"lesson.ogv", "video/ogg"},
        };
        for (String[] c : cases) {
            Long lessonId = addLesson("Lesson " + c[0]);
            String body = upload(lessonId, video(c[0], c[1].isEmpty() ? null : c[1]));
            assertTrue(body.contains("/uploads/videos/"), "expected " + c[0] + " to be accepted: " + body);
        }
    }

    /** TEST 2: awkward but legitimate file names are accepted; the stored name is generated. */
    @Test
    void awkwardFileNamesAreAcceptedAndNeverUsedAsThePath() throws Exception {
        String longName = "l".repeat(240) + ".mp4";
        String[] names = {
            "my lesson video.mp4",
            "lesson (final) (v2).mp4",
            "पाठ-वीडियो-हिंदी.mp4",
            "課程影片.webm",
            longName,
            "../../../../etc/passwd.mp4",
            "video;name&weird$chars.mov",
        };
        for (String name : names) {
            Long lessonId = addLesson("Awkward " + Math.abs(name.hashCode()));
            String body = upload(lessonId, video(name, "video/mp4"));
            assertTrue(body.contains("/uploads/videos/"), "expected " + name + " to be accepted: " + body);
            String storedUrl = lessons.findById(lessonId).orElseThrow().getVideoUrl();
            assertFalse(storedUrl.contains(".."), "stored url must not contain traversal: " + storedUrl);
            assertFalse(storedUrl.contains("passwd"), "stored url must not reuse the name: " + storedUrl);
            assertFalse(storedUrl.contains(" "), "stored url must not contain spaces: " + storedUrl);
            assertTrue(storedUrl.startsWith("/uploads/videos/"), storedUrl);
        }
    }

    /** TEST 3: unsupported formats, fake containers and non-videos are rejected. */
    @Test
    void unsupportedAndInvalidFilesAreRejectedWithAMeaningfulReason() throws Exception {
        Long lessonId = addLesson("Rejections");

        // Unsupported video-ish format.
        mvc.perform(putVideo(lessonId).file(video("lesson.wmv", "video/x-ms-wmv"))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        // Dangerous type renamed to look harmless.
        mvc.perform(putVideo(lessonId).file(new MockMultipartFile(
                        "file", "payload.mp4.exe", "application/octet-stream", payloadFor("mp4")))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest());

        // Correct extension + perfect MIME type, but the bytes are not a video at all.
        mvc.perform(putVideo(lessonId).file(new MockMultipartFile(
                        "file", "fake.mp4", "video/mp4",
                        "<?php echo 'pwned'; ?>".getBytes(StandardCharsets.UTF_8)))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest());

        // A real WebM renamed to .mp4 — extension and content must agree.
        mvc.perform(putVideo(lessonId).file(new MockMultipartFile(
                        "file", "renamed.mp4", "video/mp4", payloadFor("webm")))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest());

        // Plain document.
        mvc.perform(putVideo(lessonId).file(new MockMultipartFile(
                        "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest());

        // Empty file.
        mvc.perform(putVideo(lessonId).file(new MockMultipartFile(
                        "file", "empty.mp4", "video/mp4", new byte[0]))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isBadRequest());

        // After every rejection the lesson still has no video.
        mvc.perform(get("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());
    }

    /* -------------------------------------------------------- lesson association */

    /** TEST 4: the upload lands on the addressed lesson only — siblings stay untouched. */
    @Test
    void videoIsStoredOnTheCorrectCourseModuleAndLessonOnly() throws Exception {
        Long first = addLesson("First lesson");
        Long second = addLesson("Second lesson");

        upload(first, video("first.mp4", "video/mp4"));

        CourseLesson stored = lessons.findById(first).orElseThrow();
        assertTrue(stored.getVideoUrl().contains("/uploads/videos/"));
        assertEquals(moduleA, stored.getModule().getId());
        assertEquals("FMTA", stored.getModule().getCourse().getCode());

        mvc.perform(get("/api/v1/teacher/lessons/" + second + "/video")
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());

        // Replacing keeps the same lesson and only swaps the url.
        String before = stored.getVideoUrl();
        upload(first, video("replacement.webm", "video/webm"));
        CourseLesson replaced = lessons.findById(first).orElseThrow();
        assertFalse(before.equals(replaced.getVideoUrl()));
        assertEquals("First lesson", replaced.getTitle());
    }

    /** TEST 5: a teacher cannot upload onto another teacher's lesson. */
    @Test
    void uploadingOntoAnotherTeachersLessonIsForbidden() throws Exception {
        mvc.perform(putVideo(otherTeacherLesson).file(video("sneaky.mp4", "video/mp4"))
                        .header(U, "FMTTEACHERA").header(T, "fmt-tok-a"))
                .andExpect(status().isForbidden());

        assertTrue(lessons.findById(otherTeacherLesson).orElseThrow().getVideoUrl() == null);
    }

    /** TEST 6: an unauthenticated upload is rejected before any validation happens. */
    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        Long lessonId = addLesson("Unauthenticated");
        mvc.perform(putVideo(lessonId).file(video("anon.mp4", "video/mp4")))
                .andExpect(status().isUnauthorized());
    }

    /** TEST 7: the success response carries useful data and no internals. */
    @Test
    void successResponseExposesNoServerInternals() throws Exception {
        Long lessonId = addLesson("Response shape");
        String body = upload(lessonId, video("response check.mp4", "video/mp4"));

        assertTrue(body.contains("/uploads/videos/"), body);
        assertFalse(body.toLowerCase().contains("c:\\"), body);
        assertFalse(body.contains("/home/"), body);
        assertFalse(body.contains("uploads/videos/../"), body);
        assertFalse(body.toLowerCase().contains("token"), body);
        assertFalse(body.toLowerCase().contains("password"), body);
        assertFalse(body.toLowerCase().contains("secret"), body);
    }
}

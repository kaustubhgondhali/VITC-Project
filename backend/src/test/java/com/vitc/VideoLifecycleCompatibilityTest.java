package com.vitc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.vitc.security.VideoAccessTokenService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * PART 6C-2D/8 - VIDEO LIFECYCLE COMPATIBILITY.
 *
 * <p>Everything below is already enforced by {@code TeacherCourseContentServiceImpl} (PART 6A/6B),
 * {@code FileStorageServiceImpl} (upload/delete), and {@code StudentVideoStreamServiceImpl} /
 * {@code StudentLearningServiceImpl} (PART 6C-2A). This test does not change any of that
 * behaviour - it proves the two sides of the system (Teacher Admin write path and Student
 * Portal read/stream path) stay correct together across a full Replace/Remove lifecycle,
 * including the case existing single-side tests do not cover: a token issued to a student
 * <em>before</em> the teacher changes the video must never keep serving stale content or a
 * stale file handle after that change.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VideoLifecycleCompatibilityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;
    @Autowired VideoAccessTokenService tokenService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";
    private static final String SU = "X-Student-Id";
    private static final String ST = "X-Student-Token";

    private Long teacherId;
    private Long courseId;
    private Long moduleId;
    private Long studentId;

    @BeforeEach
    void seed() {
        User teacher = users.save(User.builder()
                .fullName("Lifecycle Teacher").email("lifecycle-t@test.io").username("LIFECYCLETEACHER")
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build());
        teacher.setSessionToken("ltok");
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacher);
        teacherId = teacher.getId();

        Course course = courses.save(Course.builder().code("LIFECYCLE").title("Lifecycle Course")
                .price(BigDecimal.TEN).active(true).teacherId(teacherId).build());
        courseId = course.getId();
        moduleId = modules.save(CourseModule.builder().course(course).title("M1")
                .displayOrder(1).active(true).build()).getId();

        User student = users.save(User.builder().fullName("Lifecycle Student").email("lifecycle-s@test.io")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE)
                .studentLoginId("VITCLIFECYCLE").build());
        student.setSessionToken("stok");
        student.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(student);
        studentId = student.getId();

        enrollments.save(Enrollment.builder().studentName("Lifecycle Student").email(student.getEmail())
                .phone("-").course(course).user(student).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());
    }

    /* ---------------------------- helpers ---------------------------- */

    private Long addLesson(String title) throws Exception {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/teacher/modules/" + moduleId + "/lessons")
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private static MockMultipartFile videoFile(String name, String contentType, byte marker) {
        // Distinct marker byte per upload so "old" vs "new" video content is trivially
        // distinguishable when asserting which file the stream endpoint actually served.
        return new MockMultipartFile("file", name, contentType,
                new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', marker, marker, marker});
    }

    private static MockMultipartHttpServletRequestBuilder putVideo(Long lessonId) {
        return multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video");
    }

    private String uploadVideo(Long lessonId, String name, String contentType, byte marker, String duration)
            throws Exception {
        String body = mvc.perform(putVideo(lessonId)
                        .file(videoFile(name, contentType, marker))
                        .param("duration", duration)
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"videoUrl\":\"([^\"]+)\".*", "$1");
    }

    private Path diskPathFor(String videoUrl) {
        String relative = videoUrl.substring(publicPath.length() + 1);
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve(relative);
    }

    /** Mints a token the way the Student Portal would, by fetching the lesson detail. */
    private String studentTokenFor(Long lessonId) throws Exception {
        String body = mvc.perform(get("/api/v1/student/lessons/" + lessonId)
                        .header(SU, "VITCLIFECYCLE").header(ST, "stok"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*token=([a-zA-Z0-9\\-_.]+).*", "$1");
    }

    /* ============================================================ TEST 1/2/3/4 ============= */

    /** TEST 1 + 2 + 3 + 4: existing video plays, replace succeeds, new video plays, old is gone -
     *  including for a token that was already issued before the replace happened. */
    @Test
    void replaceVideoLifecycle_oldGoneNewPlays_evenForAPreIssuedToken() throws Exception {
        Long lessonId = addLesson("Replace Lifecycle");

        // Old Video -> Student Opens Lesson -> (existing) video plays.
        String oldUrl = uploadVideo(lessonId, "old.mp4", "video/mp4", (byte) 0xAA, "10:00");
        Path oldPath = diskPathFor(oldUrl);
        assertTrue(Files.exists(oldPath), "the original video must exist before replacement");

        String tokenIssuedBeforeReplace = studentTokenFor(lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", tokenIssuedBeforeReplace))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', (byte) 0xAA, (byte) 0xAA, (byte) 0xAA}));

        // Teacher selects Replace Video -> New Video Upload -> New File Stored ->
        // CourseLesson.videoUrl Updated -> Old Local File Removed.
        String newUrl = uploadVideo(lessonId, "new.mp4", "video/mp4", (byte) 0xBB, "12:00");
        assertNotEquals(oldUrl, newUrl, "replacing must produce a new stored file, not overwrite in place");
        Path newPath = diskPathFor(newUrl);

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        assertEquals(newUrl, stored.getVideoUrl(), "CourseLesson.videoUrl must point at the new video");
        assertFalse(Files.exists(oldPath), "the old local file must be removed after a successful replace");
        assertTrue(Files.exists(newPath), "the new local file must exist after a successful replace");

        // Student Opens Lesson -> New Video Plays: a token minted AFTER the replace serves the new bytes.
        String tokenAfterReplace = studentTokenFor(lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", tokenAfterReplace))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', (byte) 0xBB, (byte) 0xBB, (byte) 0xBB}));

        // Compatibility guarantee: the token issued BEFORE the replace is not bound to the old file
        // handle - because the stream endpoint re-resolves the lesson's current videoUrl on every
        // request, redeeming it now serves the NEW video rather than erroring or serving stale bytes.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", tokenIssuedBeforeReplace))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', (byte) 0xBB, (byte) 0xBB, (byte) 0xBB}));

        // The raw old URL was never directly servable in the first place (VideoDirectAccessInterceptor),
        // so "no longer available through the lesson" is proven above by the file itself being gone.
        mvc.perform(get(oldUrl)).andExpect(status().isForbidden());
    }

    /* ============================================================ TEST 5 ==================== */

    /** TEST 5: a failed replacement must never disturb the video the student is currently watching. */
    @Test
    void failedReplacement_leavesTheCurrentlyPlayingVideoUntouched() throws Exception {
        Long lessonId = addLesson("Failed Replace Lifecycle");
        String goodUrl = uploadVideo(lessonId, "good.mp4", "video/mp4", (byte) 0x11, "05:00");
        Path goodPath = diskPathFor(goodUrl);

        String token = studentTokenFor(lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", token))
                .andExpect(status().isOk());

        // Teacher attempts an invalid replacement (unsupported file type).
        mvc.perform(putVideo(lessonId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok"))
                .andExpect(status().isBadRequest());

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        assertEquals(goodUrl, stored.getVideoUrl(), "videoUrl must be unchanged after a failed replacement");
        assertTrue(Files.exists(goodPath), "the original video file must still exist after a failed replacement");

        // The student's existing token still streams the untouched original video.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .bytes(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', (byte) 0x11, (byte) 0x11, (byte) 0x11}));
    }

    /* ============================================================ TEST 6/7/8 ================= */

    /** TEST 6 + 7 + 8: remove clears the url and deletes the file, a pre-issued token can no longer
     *  retrieve it, and the lesson/module/course/enrollment/progress all survive untouched. */
    @Test
    void removeVideoLifecycle_becomesInaccessible_lessonAndEnrollmentSurvive() throws Exception {
        Long lessonId = addLesson("Remove Lifecycle");
        String url = uploadVideo(lessonId, "to-remove.mp4", "video/mp4", (byte) 0x22, "06:00");
        Path path = diskPathFor(url);
        assertTrue(Files.exists(path));

        String tokenIssuedBeforeRemove = studentTokenFor(lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", tokenIssuedBeforeRemove))
                .andExpect(status().isOk());

        // Teacher removes video -> CourseLesson.videoUrl = null -> Local video deleted.
        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        assertNull(stored.getVideoUrl());
        assertFalse(Files.exists(path), "the physical file must be deleted after removal");

        // TEST 7: Student cannot retrieve the media-less lesson through the lesson API - the
        // final media-availability rule rejects it before any empty playable payload is returned.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId)
                        .header(SU, "VITCLIFECYCLE").header(ST, "stok"))
                .andExpect(status().isForbidden());
        // ...nor the token that was already issued before the removal.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", tokenIssuedBeforeRemove))
                .andExpect(status().isNotFound());

        // TEST 8: the lesson itself remains, along with its module/course/enrollment/progress.
        assertEquals("Remove Lifecycle", stored.getTitle());
        assertTrue(modules.findById(moduleId).isPresent(), "module must still exist");
        assertTrue(courses.findById(courseId).isPresent(), "course must still exist");
        assertTrue(enrollments.findFirstByUserIdAndCourseId(studentId, courseId)
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .isPresent(), "enrollment must be untouched");
    }

    /* ============================================================ TEST 9 ===================== */

    /** TEST 9: an external URL keeps working across the same lifecycle - never treated as a local
     *  path, never deleted from disk, and never routed through the protected stream endpoint. */
    @Test
    void externalVideoUrl_survivesTheLifecycleUnchanged() throws Exception {
        Long lessonId = addLesson("External Lifecycle");
        String external = "https://example.com/video.mp4";

        mvc.perform(put("/api/v1/teacher/lessons/" + lessonId)
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"External Lifecycle\",\"videoUrl\":\"" + external + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(external));

        // Student Portal returns the external url exactly as stored - never rewritten into a
        // token-bearing local streaming url, since it was never a VITC-managed local file.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId)
                        .header(SU, "VITCLIFECYCLE").header(ST, "stok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(external));

        // The protected local-stream endpoint correctly refuses to serve it (it has no local file).
        String bogusToken = tokenService.issue(studentId, lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", bogusToken))
                .andExpect(status().isNotFound());

        // Removing it clears the url but never touches the filesystem (nothing local to delete).
        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok"))
                .andExpect(status().isOk());
        assertNull(lessons.findById(lessonId).orElseThrow().getVideoUrl());

        // Re-adding an external url and then replacing it with a real local upload must not attempt
        // any filesystem delete of the external url either (PART 6A).
        mvc.perform(put("/api/v1/teacher/lessons/" + lessonId)
                        .header(TU, "LIFECYCLETEACHER").header(TT, "ltok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"External Lifecycle\",\"videoUrl\":\"" + external + "\"}"))
                .andExpect(status().isOk());
        String localUrl = uploadVideo(lessonId, "replaces-external.mp4", "video/mp4", (byte) 0x33, "01:00");
        assertTrue(Files.exists(diskPathFor(localUrl)));
        assertEquals(localUrl, lessons.findById(lessonId).orElseThrow().getVideoUrl());
    }

    /* ============================================================ TEST 10/11 ================= */

    /** TEST 10 + 11: an authorized (enrolled) student can stream the current video; an unauthorized
     *  (not enrolled) student remains blocked, even with a well-formed token for their own account. */
    @Test
    void authorizedStudentStreams_unauthorizedStudentStaysBlocked() throws Exception {
        Long lessonId = addLesson("Access Lifecycle");
        uploadVideo(lessonId, "access.mp4", "video/mp4", (byte) 0x44, "02:00");

        // TEST 10: the enrolled student streams the current video successfully.
        String authorizedToken = studentTokenFor(lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", authorizedToken))
                .andExpect(status().isOk());

        // TEST 11: a different student, not enrolled in this course, is blocked at the lesson
        // lookup itself...
        User outsider = users.save(User.builder().fullName("Outsider Student").email("outsider@test.io")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE)
                .studentLoginId("VITCOUTSIDER").build());
        outsider.setSessionToken("otok");
        outsider.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(outsider);

        mvc.perform(get("/api/v1/student/lessons/" + lessonId)
                        .header(SU, "VITCOUTSIDER").header(ST, "otok"))
                .andExpect(status().isForbidden());

        // ...and even a well-formed, correctly-signed token minted for that outsider's own account
        // is rejected by the streaming endpoint itself, which re-checks enrolment fresh every time.
        String outsiderToken = tokenService.issue(outsider.getId(), lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", outsiderToken))
                .andExpect(status().isForbidden());
    }
}

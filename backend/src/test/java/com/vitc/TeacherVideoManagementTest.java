package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * PART 9C / PART 2-8: Teacher video management + authorisation.
 *
 * <p>A "video" here is still the existing {@code course_lessons} row (url + duration + order +
 * active) - there is no {@code teacher_videos} table. Since PART 2/8 the write path is a
 * {@code multipart/form-data} file upload rather than a JSON {@code videoUrl} string; the uploaded
 * file is stored under {@code uploads/videos/} via {@code FileStorageService.uploadVideo}, and only
 * the resulting public URL lands in {@code CourseLesson.videoUrl}. The tests prove the chain
 * authenticated teacher -&gt; assigned course -&gt; module -&gt; lesson -&gt; video, and that the same
 * stored row is what the write path updates (so the Student Portal automatically sees it).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherVideoManagementTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";

    private Long moduleA;
    private Long lessonB;

    @BeforeEach
    void seed() {
        User a = teacher("vid-a@test.io", "VIDTEACHERA", "vtok-a");
        User b = teacher("vid-b@test.io", "VIDTEACHERB", "vtok-b");
        Course ca = course("VIDA", "Video Test Java", a.getId());
        Course cb = course("VIDB", "Video Test Python", b.getId());
        moduleA = modules.save(CourseModule.builder()
                .course(ca).title("Java Fundamentals").displayOrder(1).active(true).build()).getId();
        CourseModule mb = modules.save(CourseModule.builder()
                .course(cb).title("Python Basics").displayOrder(1).active(true).build());
        lessonB = lessons.save(CourseLesson.builder()
                .module(mb).title("B lesson").displayOrder(1).active(true).build()).getId();
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
                        .header(U, "VIDTEACHERA").header(T, "vtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    /**
     * A tiny but genuine container payload. PART 2/10 validates the real bytes of an upload, so
     * the header has to match the extension: ISO-BMFF ("ftyp") for MP4/MOV, EBML for WebM/MKV.
     */
    private static MockMultipartFile videoFile(String originalName, String contentType) {
        boolean matroska = originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".webm")
                || originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".mkv");
        byte[] head = matroska
                ? new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04}
                : new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        byte[] payload = new byte[64];
        System.arraycopy(head, 0, payload, 0, head.length);
        return new MockMultipartFile("file", originalName, contentType, payload);
    }

    private static MockMultipartHttpServletRequestBuilder putVideo(Long lessonId) {
        return multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video");
    }

    @Test
    void teacherCanUploadEditAndRemoveTheVideoOfOwnLesson() throws Exception {
        Long lessonId = addLesson("Introduction");

        mvc.perform(putVideo(lessonId)
                        .file(videoFile("intro.mp4", "video/mp4"))
                        .param("duration", "12:40")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.containsString("/uploads/videos/")))
                .andExpect(jsonPath("$.data.duration").value("12:40"));

        // Replace the video + its duration with a second upload.
        mvc.perform(putVideo(lessonId)
                        .file(videoFile("intro-v2.webm", "video/webm"))
                        .param("duration", "15:00")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.containsString(".webm")));

        mvc.perform(get("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duration").value("15:00"));

        // Same row in the existing course_lessons table -> Student Portal reads the update.
        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(stored.getVideoUrl().contains("/uploads/videos/"));
        org.junit.jupiter.api.Assertions.assertEquals("15:00", stored.getDuration());

        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());
    }

    /** Resolves the on-disk path for a "/uploads/videos/xxx.mp4" style url (see app.upload.dir). */
    private static java.nio.file.Path diskPathFor(String videoUrl) {
        // videoUrl looks like "/uploads/videos/2026-08-15-abcdef12.mp4"; app.upload.public-path is
        // "/uploads" and app.upload.dir is "uploads", so strip the leading "/uploads/" and resolve
        // the remainder ("videos/...") against the same "uploads" root FileStorageServiceImpl uses.
        String relative = videoUrl.substring("/uploads/".length());
        return java.nio.file.Paths.get("uploads", relative);
    }

    /** PART 6A/8: replacing a local video must delete the old file only after the new one is live. */
    @Test
    void replacingLocalVideoDeletesTheOldFileAfterSuccess() throws Exception {
        Long lessonId = addLesson("Replace local");

        String firstUrl = mvc.perform(putVideo(lessonId)
                        .file(videoFile("first.mp4", "video/mp4"))
                        .param("duration", "05:00")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"videoUrl\":\"([^\"]+)\".*", "$1");

        java.nio.file.Path firstPath = diskPathFor(firstUrl);
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(firstPath),
                "first uploaded video should exist on disk before replacement");

        mvc.perform(putVideo(lessonId)
                        .file(videoFile("second.mp4", "video/mp4"))
                        .param("duration", "06:00")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.not(firstUrl)));

        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(firstPath),
                "old local video file should be deleted once the replacement succeeds");

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstUrl, stored.getVideoUrl());
        org.junit.jupiter.api.Assertions.assertTrue(
                java.nio.file.Files.exists(diskPathFor(stored.getVideoUrl())),
                "new video file should exist on disk");
    }

    /** PART 6A/8: a failed replacement must never delete or lose the existing video. */
    @Test
    void failedReplacementKeepsTheExistingVideoIntact() throws Exception {
        Long lessonId = addLesson("Replace failure");

        String firstUrl = mvc.perform(putVideo(lessonId)
                        .file(videoFile("keep-me.mp4", "video/mp4"))
                        .param("duration", "03:30")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"videoUrl\":\"([^\"]+)\".*", "$1");
        java.nio.file.Path firstPath = diskPathFor(firstUrl);

        // Invalid replacement attempt (unsupported file type) - must be rejected before anything
        // about the lesson or the old file is touched.
        mvc.perform(putVideo(lessonId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isBadRequest());

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(firstUrl, stored.getVideoUrl(),
                "videoUrl must be unchanged after a failed replacement");
        org.junit.jupiter.api.Assertions.assertEquals("03:30", stored.getDuration());
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(firstPath),
                "the original video file must still exist after a failed replacement");
    }

    /** PART 6A/8: replacing an external URL never attempts to delete anything from disk. */
    @Test
    void replacingAnExternalUrlWithALocalUploadNeverTouchesTheFilesystem() throws Exception {
        Long lessonId = addLesson("External to local");

        // Seed the lesson directly with an external URL (as a Teacher would via the JSON edit
        // path) instead of going through the multipart upload endpoint.
        CourseLesson lesson = lessons.findById(lessonId).orElseThrow();
        lesson.setVideoUrl("https://example.com/video.mp4");
        lessons.save(lesson);

        mvc.perform(putVideo(lessonId)
                        .file(videoFile("local.mp4", "video/mp4"))
                        .param("duration", "04:20")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.containsString("/uploads/videos/")));

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(stored.getVideoUrl().startsWith("/uploads/videos/"));
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(diskPathFor(stored.getVideoUrl())));
    }

    /** PART 6B/8: removing a local video clears the url, deletes the file, and keeps everything else. */
    @Test
    void removingLocalVideoClearsUrlDeletesFileAndPreservesLessonModuleAndCourse() throws Exception {
        Long lessonId = addLesson("Remove local");

        String videoUrl = mvc.perform(putVideo(lessonId)
                        .file(videoFile("remove-me.mp4", "video/mp4"))
                        .param("duration", "07:15")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"videoUrl\":\"([^\"]+)\".*", "$1");
        java.nio.file.Path filePath = diskPathFor(videoUrl);
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(filePath));

        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.title").value("Remove local"));

        // videoUrl gone, physical file gone.
        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(stored.getVideoUrl());
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(filePath),
                "the physical video file should be deleted after removal");

        // Lesson itself, its identity/fields, the module and the course are all untouched.
        org.junit.jupiter.api.Assertions.assertEquals("Remove local", stored.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals(1, stored.getDisplayOrder());
        org.junit.jupiter.api.Assertions.assertTrue(stored.getActive());
        org.junit.jupiter.api.Assertions.assertTrue(modules.findById(moduleA).isPresent(), "module must still exist");
        org.junit.jupiter.api.Assertions.assertTrue(
                courses.findByCode("VIDA").isPresent(), "course must still exist");
    }

    /** PART 6B/8: removing an external URL clears videoUrl but never touches the filesystem. */
    @Test
    void removingExternalVideoUrlNeverAttemptsAFilesystemDelete() throws Exception {
        Long lessonId = addLesson("Remove external");
        CourseLesson lesson = lessons.findById(lessonId).orElseThrow();
        lesson.setVideoUrl("https://example.com/video.mp4");
        lessons.save(lesson);

        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonId + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").doesNotExist());

        CourseLesson stored = lessons.findById(lessonId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(stored.getVideoUrl());
        org.junit.jupiter.api.Assertions.assertEquals("Remove external", stored.getTitle());
    }

    @Test
    void rejectsUnsupportedFileTypes() throws Exception {
        Long lessonId = addLesson("Bad upload");

        mvc.perform(putVideo(lessonId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertNull(lessons.findById(lessonId).orElseThrow().getVideoUrl());
    }

    @Test
    void editingLessonWithoutVideoFieldsKeepsTheStoredVideo() throws Exception {
        Long lessonId = addLesson("Variables");
        mvc.perform(putVideo(lessonId)
                        .file(videoFile("vars.mp4", "video/mp4"))
                        .param("duration", "08:10")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isOk());

        CourseLesson before = lessons.findById(lessonId).orElseThrow();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/teacher/lessons/" + lessonId)
                        .header(U, "VIDTEACHERA").header(T, "vtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Variables & Types\",\"description\":\"Declaring variables\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(before.getVideoUrl()))
                .andExpect(jsonPath("$.data.duration").value("08:10"));
    }

    @Test
    void teacherCannotTouchVideoOfAnotherTeachersLesson() throws Exception {
        mvc.perform(putVideo(lessonB)
                        .file(videoFile("x.mp4", "video/mp4"))
                        .param("duration", "01:00")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/teacher/lessons/" + lessonB + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonB + "/video")
                        .header(U, "VIDTEACHERA").header(T, "vtok-a"))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertNull(lessons.findById(lessonB).orElseThrow().getVideoUrl());
    }

    @Test
    void videoEndpointsRequireAnAuthenticatedTeacher() throws Exception {
        Long lessonId = addLesson("Data Types");
        mvc.perform(putVideo(lessonId).file(videoFile("x.mp4", "video/mp4")))
                .andExpect(status().isUnauthorized());
    }
}

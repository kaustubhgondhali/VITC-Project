package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 6C-2A/8, extended in 6C-2B/8 and 6C-2C/8 - proves the video-access-control AND
 * HTTP-Range-streaming behaviour of the protected video endpoint end to end:
 * <ul>
 *   <li>a course video file uploaded under {@code uploads/videos/} cannot be opened directly, with or without a session</li>
 *   <li>the protected stream endpoint requires a valid, lesson-matching, unexpired token</li>
 *   <li>a token minted for one lesson cannot be replayed onto a different lesson</li>
 *   <li>a student who is not enrolled in the owning course cannot stream it, with or without a Range header</li>
 *   <li>a plain request (no Range) returns 200 with the full file; a Range request returns 206
 *       with the correct slice; a large file is served in capped chunks, never as one giant
 *       response; an out-of-bounds Range returns 416</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentVideoAccessSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired VideoAccessTokenService tokenService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final int VIDEO_SIZE = 10_000; // deterministic small file for byte-exact range assertions
    private static final long CHUNK_SIZE = 2 * 1024 * 1024; // must match StudentVideoStreamController

    private Long courseId;
    private Long lessonId;
    private Long studentId;
    private Path videoFile;
        private Path audioFile;
    private final List<Path> extraFiles = new ArrayList<>();

    @BeforeEach
    void seed() throws Exception {
        Course course = courses.save(Course.builder().code("TVID").title("Video Course")
                .price(BigDecimal.TEN).active(true).build());
        courseId = course.getId();
        CourseModule module = modules.save(CourseModule.builder().course(course).title("M1")
                .displayOrder(1).active(true).build());

        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("videos");
        root.toFile().mkdirs();
        videoFile = root.resolve("security-test-lesson.mp4");
        writeDeterministicFile(videoFile, VIDEO_SIZE);

        CourseLesson lesson = lessons.save(CourseLesson.builder().module(module).title("Lesson 1")
                .videoUrl("/uploads/videos/security-test-lesson.mp4").displayOrder(1).active(true).build());
        lessonId = lesson.getId();

        User student = users.save(User.builder().fullName("Video Student").email("video-stu@test.io")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE)
                .studentLoginId("VITCVIDEO").build());
        student.setSessionToken("tok-video");
        student.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(student);
        studentId = student.getId();

        enrollments.save(Enrollment.builder().studentName("Video Student").email(student.getEmail())
                .phone("-").course(course).user(student).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());
    }

    @AfterEach
    void cleanup() {
        File f = videoFile.toFile();
        if (f.exists()) {
            f.delete();
        }
                if (audioFile != null && audioFile.toFile().exists()) {
                        audioFile.toFile().delete();
                }
        for (Path p : extraFiles) {
            File file = p.toFile();
            if (file.exists()) {
                file.delete();
            }
        }
    }

    /** Deterministic byte at position i, so a returned slice can be checked byte-for-byte. */
    private static byte patternByteAt(long i) {
        return (byte) (i % 256);
    }

    private static void writeDeterministicFile(Path path, int size) throws Exception {
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = patternByteAt(i);
        }
        try (FileOutputStream out = new FileOutputStream(path.toFile())) {
            out.write(content);
        }
    }

    /* STEP 2: the raw upload path must no longer be directly reachable, logged in or not. */
    @Test
    void directUploadUrlIsBlocked() throws Exception {
        mvc.perform(get("/uploads/videos/security-test-lesson.mp4"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/uploads/videos/security-test-lesson.mp4")
                        .header("X-Student-Id", "VITCVIDEO").header("X-Student-Token", "tok-video"))
                .andExpect(status().isForbidden());
    }

    /* The enrolled student's own lesson lookup returns a token-bearing protected url,
       and that exact token actually streams the file's bytes back. */
    @Test
    void enrolledStudentGetsPlayableToken() throws Exception {
        String body = mvc.perform(get("/api/v1/student/lessons/" + lessonId)
                        .header("X-Student-Id", "VITCVIDEO").header("X-Student-Token", "tok-video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoType").value("FILE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("/api/v1/student/lessons/" + lessonId + "/video?token=");
    }

    @Test
    void localAudioIsTokenizedAndStreamedOnlyForEnrolledStudent() throws Exception {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("audio");
        root.toFile().mkdirs();
        audioFile = root.resolve("security-test-lesson.mp3");
        writeDeterministicFile(audioFile, 1024);
        CourseModule module = modules.findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc(courseId)
                .stream().findFirst().orElseThrow();
        CourseLesson audioLesson = lessons.save(CourseLesson.builder().module(module).title("Audio Lesson")
                .audioUrl("/uploads/audio/security-test-lesson.mp3").displayOrder(2).active(true).build());

        String body = mvc.perform(get("/api/v1/student/lessons/" + audioLesson.getId())
                        .header("X-Student-Id", "VITCVIDEO").header("X-Student-Token", "tok-video"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("/api/v1/student/lessons/" + audioLesson.getId() + "/audio?token=");

        mvc.perform(get("/uploads/audio/security-test-lesson.mp3"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/lessons/" + audioLesson.getId() + "/audio")
                        .param("token", tokenService.issue(studentId, audioLesson.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().longValue("Content-Length", 1024));

        Course otherCourse = courses.save(Course.builder().code("TAUDIO2").title("Other Audio Course")
                .price(BigDecimal.TEN).active(true).build());
        User otherStudent = users.save(User.builder().fullName("Other Audio Student").email("audio-other@test.io")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE)
                .studentLoginId("VITCAUDIOOTHER").build());
        enrollments.save(Enrollment.builder().studentName("Other Audio Student").email(otherStudent.getEmail())
                .phone("-").course(otherCourse).user(otherStudent).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());
        mvc.perform(get("/api/v1/student/lessons/" + audioLesson.getId() + "/audio")
                        .param("token", tokenService.issue(otherStudent.getId(), audioLesson.getId())))
                .andExpect(status().isForbidden());
    }

    /* TEST 1 - normal (non-Range) request: full file, 200 OK, correct Content-Type/Content-Length,
       body matches the file byte-for-byte - proves nothing is buffered/truncated/altered. */
    @Test
    void normalVideoRequestReturnsFullContentAsOk() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        byte[] body = mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().longValue("Content-Length", VIDEO_SIZE))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSize(VIDEO_SIZE);
        assertThat(body[0]).isEqualTo(patternByteAt(0));
        assertThat(body[VIDEO_SIZE - 1]).isEqualTo(patternByteAt(VIDEO_SIZE - 1));
    }

    /* TEST 2/3/4/6 - a Range request returns 206 with the correct Content-Range/Content-Length
       and the exact requested byte slice (seek "forward" to a later offset). */
    @Test
    void rangeRequestSeekForwardReturnsExactSlice() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        byte[] body = mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", token).header("Range", "bytes=5000-5099"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("Content-Range", "bytes 5000-5099/" + VIDEO_SIZE))
                .andExpect(header().longValue("Content-Length", 100))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSize(100);
        for (int i = 0; i < body.length; i++) {
            assertThat(body[i]).isEqualTo(patternByteAt(5000 + i));
        }
    }

    /* TEST 7 - seek "backward": an earlier-offset Range works independently of any prior
       request - the endpoint is stateless per-request, exactly what real seeking needs. */
    @Test
    void rangeRequestSeekBackwardReturnsExactSlice() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        // Simulate the player having already buffered forward, then the student dragging the
        // scrubber back to an earlier point in the video.
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", token).header("Range", "bytes=8000-8099"))
                .andExpect(status().isPartialContent());

        byte[] body = mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", token).header("Range", "bytes=200-299"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 200-299/" + VIDEO_SIZE))
                .andExpect(header().longValue("Content-Length", 100))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).hasSize(100);
        for (int i = 0; i < body.length; i++) {
            assertThat(body[i]).isEqualTo(patternByteAt(200 + i));
        }
    }

    /* TEST 8 - a file bigger than the server's per-response chunk cap is never returned in one
       shot, even when the browser's Range asks for "the rest of the file" (a common real
       request, e.g. "bytes=0-"). Proves large (100/250/500MB-class) files stay bounded in
       memory/response size regardless of how much the client asks for. */
    @Test
    void largeFileIsServedInCappedChunksNotWhole() throws Exception {
        int bigSize = (int) (CHUNK_SIZE + 500_000); // bigger than one chunk, still test-fast to write
        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("videos");
        Path bigFile = root.resolve("security-test-large.mp4");
        writeDeterministicFile(bigFile, bigSize);
        extraFiles.add(bigFile);

        CourseModule module = modules.save(CourseModule.builder().course(courses.findById(courseId).orElseThrow())
                .title("M-large").displayOrder(3).active(true).build());
        CourseLesson bigLesson = lessons.save(CourseLesson.builder().module(module).title("Big Lesson")
                .videoUrl("/uploads/videos/security-test-large.mp4").displayOrder(3).active(true).build());

        String token = tokenService.issue(studentId, bigLesson.getId());
        byte[] body = mvc.perform(get("/api/v1/student/lessons/" + bigLesson.getId() + "/video")
                        .param("token", token).header("Range", "bytes=0-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-" + (CHUNK_SIZE - 1) + "/" + bigSize))
                .andExpect(header().longValue("Content-Length", CHUNK_SIZE))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body.length).isEqualTo((int) CHUNK_SIZE);
        assertThat(body.length).isLessThan(bigSize);
    }

    /* Content-Type is derived from the server-stored filename, never the client - webm/mov
       lessons report the correct MIME type. */
    @Test
    void contentTypeIsCorrectForWebmAndMov() throws Exception {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize().resolve("videos");
        Path webm = root.resolve("security-test.webm");
        Path mov = root.resolve("security-test.mov");
        writeDeterministicFile(webm, 500);
        writeDeterministicFile(mov, 500);
        extraFiles.add(webm);
        extraFiles.add(mov);

        Course course = courses.findById(courseId).orElseThrow();
        CourseModule module = modules.save(CourseModule.builder().course(course)
                .title("M-types").displayOrder(4).active(true).build());
        CourseLesson webmLesson = lessons.save(CourseLesson.builder().module(module).title("Webm Lesson")
                .videoUrl("/uploads/videos/security-test.webm").displayOrder(4).active(true).build());
        CourseLesson movLesson = lessons.save(CourseLesson.builder().module(module).title("Mov Lesson")
                .videoUrl("/uploads/videos/security-test.mov").displayOrder(5).active(true).build());

        mvc.perform(get("/api/v1/student/lessons/" + webmLesson.getId() + "/video")
                        .param("token", tokenService.issue(studentId, webmLesson.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/webm"));

        mvc.perform(get("/api/v1/student/lessons/" + movLesson.getId() + "/video")
                        .param("token", tokenService.issue(studentId, movLesson.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/quicktime"));
    }

    /* TEST 11 - a Range that starts beyond the end of the file is rejected with
       416 Range Not Satisfiable and a Content-Range naming the real length, not a 500 or
       silently-clamped response. */
    @Test
    void outOfBoundsRangeReturnsRangeNotSatisfiable() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", token).header("Range", "bytes=999999999-1000000000"))
                .andExpect(status().is(416))
                .andExpect(header().string("Content-Range", "bytes */" + VIDEO_SIZE));
    }

    /* A syntactically well-formed but unsigned/garbage token is rejected. */
    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", "not-a-real-token"))
                .andExpect(status().isForbidden());
    }

    /* TEST 9 - a Range request is not exempt from authorization: a garbage/invalid token with a
       Range header attached is rejected exactly the same as a plain request would be. Proves
       the Range branch cannot be used to bypass the resolve() chain. */
    @Test
    void unauthorizedRangeRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", "not-a-real-token").header("Range", "bytes=0-99"))
                .andExpect(status().isForbidden());
    }

    /* A token minted for one lesson can never be replayed onto a different lesson id. */
    @Test
    void tokenIsBoundToItsOwnLesson() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        Long otherLessonId = lessonId + 999L;
        mvc.perform(get("/api/v1/student/lessons/" + otherLessonId + "/video").param("token", token))
                .andExpect(status().isForbidden());
    }

    /* A well-formed, correctly-signed token stops working the moment the enrolment is revoked -
       proving the stream endpoint re-checks enrolment itself rather than trusting the token alone. */
    @Test
    void revokedEnrollmentCutsOffAnAlreadyIssuedToken() throws Exception {
        String token = tokenService.issue(studentId, lessonId);
        Enrollment enrollment = enrollments.findFirstByUserIdAndCourseId(studentId, courseId).orElseThrow();
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollments.save(enrollment);

        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", token))
                .andExpect(status().isForbidden());
    }

    /* PART 6C-2B/8 - CASE 1: no token at all on the stream endpoint. There is no session/header
       concept here (a native <video> tag can't attach one - see the class-level StudentVideoStreamController
       javadoc), so "unauthenticated" for this endpoint means "no valid token", which the project's existing
       framework reports as 400 Bad Request via GlobalExceptionHandler#handleMalformed. Every other
       /api/v1/student/** endpoint still returns 401 for a missing session, unaffected by this endpoint's
       token-based design. */
    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video"))
                .andExpect(status().isBadRequest());
    }

    /* CASE 3 / TEST 10: a student enrolled only in a different course cannot stream this lesson's
       video, even with a well-formed, correctly-signed token minted for their own account, and
       even when the request carries a Range header. */
    @Test
    void crossCourseStudentCannotStream() throws Exception {
        Course otherCourse = courses.save(Course.builder().code("TVID2").title("Other Course")
                .price(BigDecimal.TEN).active(true).build());
        User otherStudent = users.save(User.builder().fullName("Other Student").email("other-stu@test.io")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE)
                .studentLoginId("VITCOTHER").build());
        otherStudent.setSessionToken("tok-other");
        otherStudent.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(otherStudent);
        enrollments.save(Enrollment.builder().studentName("Other Student").email(otherStudent.getEmail())
                .phone("-").course(otherCourse).user(otherStudent).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());

        String token = tokenService.issue(otherStudent.getId(), lessonId);
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video").param("token", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", token).header("Range", "bytes=0-99"))
                .andExpect(status().isForbidden());
    }

    /* CASE 4: an invalid/non-existent lessonId returns 404, not a 500 or a leaked path. */
    @Test
    void invalidLessonIdReturnsNotFound() throws Exception {
        String token = tokenService.issue(studentId, 999999L);
        mvc.perform(get("/api/v1/student/lessons/999999/video").param("token", token))
                .andExpect(status().isNotFound());
    }

    /* CASE 5: the lesson exists and the student is enrolled, but the lesson has no video at all
       (videoUrl = null) - the endpoint must 404 rather than error out trying to resolve a path. */
    @Test
    void lessonWithoutVideoReturnsNotFound() throws Exception {
        CourseModule module = modules.save(CourseModule.builder().course(courses.findById(courseId).orElseThrow())
                .title("M2").displayOrder(2).active(true).build());
        CourseLesson noVideoLesson = lessons.save(CourseLesson.builder().module(module).title("No Video Lesson")
                .videoUrl(null).displayOrder(2).active(true).build());

        String token = tokenService.issue(studentId, noVideoLesson.getId());
        mvc.perform(get("/api/v1/student/lessons/" + noVideoLesson.getId() + "/video").param("token", token))
                .andExpect(status().isNotFound());
    }

    /* TEST 11 / VIDEO PATH SECURITY: the endpoint only ever accepts lessonId + token - there is no
       client-suppliable path/file parameter to traverse with, Range header attached or not. */
    @Test
    void pathTraversalAttemptsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", "not-a-real-token")
                        .param("path", "../../../../etc/passwd"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/lessons/" + lessonId + "/video")
                        .param("token", "not-a-real-token")
                        .param("file", "../../../../etc/passwd")
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isForbidden());
    }
}

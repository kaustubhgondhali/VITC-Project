package com.vitc.service.impl;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.ForbiddenException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.UserRepository;
import com.vitc.security.VideoAccessTokenService;
import com.vitc.service.StudentVideoStreamService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 6C-2A/8 - the security gate in front of a course video file on disk.
 *
 * <p>Re-runs the same chain {@code StudentLearningServiceImpl.lesson()} already
 * ran when it minted the token (Authentication via the token's signature,
 * Role, Enrollment, Lesson access), so a token cannot outlive a revoked
 * enrolment or a deactivated/deleted lesson even though it is still
 * cryptographically valid and unexpired. Only ever resolves a path that is
 * both inside the managed {@code uploads/videos/} directory AND exactly the
 * path stored on the lesson's own {@code videoUrl} - nothing derived from
 * client input reaches the filesystem.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentVideoStreamServiceImpl implements StudentVideoStreamService {

    private static final Set<EnrollmentStatus> ACCESS_STATES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final VideoAccessTokenService tokenService;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    private static final String VIDEO_FOLDER = "videos";
    private static final String AUDIO_FOLDER = "audio";

    @Override
    public ResolvedVideo resolve(Long lessonId, String token) {
        return resolveMedia(lessonId, token, VIDEO_FOLDER, "video");
    }

    @Override
    public ResolvedVideo resolveAudio(Long lessonId, String token) {
        return resolveMedia(lessonId, token, AUDIO_FOLDER, "audio");
    }

    private ResolvedVideo resolveMedia(Long lessonId, String token, String folder, String mediaLabel) {
        // 1) Authentication - the token's HMAC signature must verify against this server's key.
        VideoAccessTokenService.ParsedToken parsed = tokenService.parse(token)
                .orElseThrow(() -> new ForbiddenException("This " + mediaLabel
                        + " link is invalid. Please reopen the lesson."));
        if (parsed.isExpired()) {
            throw new ForbiddenException("This " + mediaLabel + " link has expired. Please reopen the lesson.");
        }
        if (parsed.lessonId() != lessonId) {
            // A token minted for a different lesson can never be replayed onto this one.
            throw new ForbiddenException("This " + mediaLabel + " link is not valid for this lesson.");
        }

        // 2) Role - the account the token was issued to must still be an active student.
        User student = userRepository.findById(parsed.studentId())
                .filter(u -> u.getRole() == UserRole.STUDENT && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Your account can no longer access this video."));

        // 3) Lesson access - lesson must still exist and be published.
        CourseLesson lesson = lessonRepository.findById(lessonId)
                .filter(l -> Boolean.TRUE.equals(l.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("This lesson is not available"));
        if (lesson.getModule() == null || !Boolean.TRUE.equals(lesson.getModule().getActive())
            || lesson.getModule().getCourse() == null
            || !Boolean.TRUE.equals(lesson.getModule().getCourse().getActive())) {
            throw new ForbiddenException("This lesson is not available");
        }
        Course course = lesson.getModule().getCourse();

        // 4) Enrollment - re-checked fresh, so a revoked/expired enrolment immediately cuts
        // off a still-unexpired token, not just future page loads.
        enrollmentRepository.findFirstByUserIdAndCourseId(student.getId(), course.getId())
                .filter(e -> ACCESS_STATES.contains(e.getStatus()))
                .orElseThrow(() -> new ForbiddenException("You do not have access to this course"));

        String mediaUrl = VIDEO_FOLDER.equals(folder) ? lesson.getVideoUrl() : lesson.getAudioUrl();
        String managedPrefix = publicPath + "/" + folder + "/";
        if (mediaUrl == null || mediaUrl.isBlank() || !mediaUrl.startsWith(managedPrefix)) {
            // External URL (YouTube/Vimeo/https link) has no local file to stream - the
            // player is expected to use it directly, never call this endpoint for it.
            throw new ResourceNotFoundException("This lesson has no downloadable " + mediaLabel + " file");
        }

        String relative = mediaUrl.substring(publicPath.length() + 1);
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        // Defence in depth: relative always comes from the DB-stored videoUrl (itself only ever
        // written by FileStorageServiceImpl with a generated name), never from client input, so
        // this can't actually escape root - the check stays cheap insurance regardless.
        // PART 6C-2C/8 - FILE VALIDATION: inside the managed directory, exists, is a regular
        // file (not a directory/symlink-to-elsewhere via traversal), and is actually readable
        // (permissions, not just presence) before the controller ever opens a stream on it.
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw new ResourceNotFoundException("The " + mediaLabel + " file for this lesson could not be found");
        }

        return new ResolvedVideo(resolved, contentTypeFor(resolved, folder));
    }

    private static String contentTypeFor(Path path, String folder) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (AUDIO_FOLDER.equals(folder)) {
            int dot = name.lastIndexOf('.');
            return com.vitc.security.upload.AudioFormats.contentTypeFor(
                    dot < 0 ? "" : name.substring(dot + 1));
        }
        return com.vitc.security.upload.VideoFormats.contentTypeForFileName(name);
    }
}

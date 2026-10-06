package com.vitc.service.impl;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.service.AdminCourseContentService;
import com.vitc.service.AuditLogService;
import com.vitc.service.FileStorageService;
import com.vitc.service.TeacherAuthorizationService;
import com.vitc.service.TeacherCourseContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ownership gate in front of the existing {@link AdminCourseContentService}.
 *
 * <p>The authorization chain is always: authenticated teacher -&gt; assigned course -&gt; module. The
 * course/module ids arriving from the browser are treated as untrusted input: each one is loaded
 * from the database and its {@code course.teacherId} compared with the session teacher id before
 * any read or write happens. A teacher editing an id by hand gets a 403 even though the id is
 * perfectly valid for someone else.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherCourseContentServiceImpl implements TeacherCourseContentService {

    private final AdminCourseContentService contentService;
    /** PART 10A: the single central Teacher authorization authority - no local ownership logic. */
    private final TeacherAuthorizationService authorization;
    /** PART 2/8: stores the uploaded video file; only its returned URL is persisted on the lesson. */
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final CourseLessonRepository lessonRepository;

    @Override
    public AdminCourseContentResponse content(Long teacherId, Long courseId) {
        assertOwnsCourse(teacherId, courseId);
        return contentService.content(courseId);
    }

    @Override
    @Transactional
    public AdminModuleResponse createModule(Long teacherId, Long courseId, AdminModuleRequest request) {
        assertOwnsCourse(teacherId, courseId);
        return contentService.createModule(courseId, request);
    }

    @Override
    @Transactional
    public AdminModuleResponse updateModule(Long teacherId, Long moduleId, AdminModuleRequest request) {
        assertOwnsModule(teacherId, moduleId);
        return contentService.updateModule(moduleId, request);
    }

    @Override
    @Transactional
    public AdminModuleResponse moveModule(Long teacherId, Long moduleId, int direction) {
        assertOwnsModule(teacherId, moduleId);
        return contentService.moveModule(moduleId, direction);
    }

    @Override
    @Transactional
    public AdminModuleResponse setModuleActive(Long teacherId, Long moduleId, boolean active) {
        CourseModule module = assertOwnsModule(teacherId, moduleId);
        // Reuse the existing update path so there is exactly one place that writes a module,
        // and keep every other field as-is (the existing `active` flag is the only status field).
        return contentService.updateModule(moduleId, new AdminModuleRequest(
                module.getTitle(), module.getDescription(), module.getDisplayOrder(), active));
    }

    @Override
    public AdminModuleResponse module(Long teacherId, Long moduleId) {
        // Ownership first, then read the module straight out of the existing course content tree so
        // there is still exactly one place that builds a module response.
        CourseModule module = assertOwnsModule(teacherId, moduleId);
        return contentService.content(module.getCourse().getId()).modules().stream()
                .filter(m -> m.id().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Module", moduleId));
    }

    @Override
    @Transactional
    public void deleteModule(Long teacherId, Long moduleId) {
        // Snapshot the lessons before the delete so managed video files can be cleaned up after the
        // database rows are gone (same order as the video replace/clear paths).
        AdminModuleResponse snapshot = module(teacherId, moduleId);
        contentService.deleteModule(moduleId);
        for (AdminLessonResponse lesson : snapshot.lessons()) {
            if (lesson.videoUrl() != null && !lesson.videoUrl().isBlank()) {
                fileStorageService.deleteVideoIfManaged(lesson.videoUrl());
            }
            if (lesson.audioUrl() != null && !lesson.audioUrl().isBlank()) {
                fileStorageService.deleteFileIfManaged(lesson.audioUrl(), "audio");
            }
        }
    }


    /* ========================= lessons (PART 9B) ========================= */

    @Override
    @Transactional
    public AdminLessonResponse createLesson(Long teacherId, Long moduleId, AdminLessonRequest request) {
        // Teacher -> assigned course -> module, verified server-side before anything is written.
        assertOwnsModule(teacherId, moduleId);
        // PART 9C: the optional video url/duration of the request are stored on the same lesson row
        // through the existing admin write path - still the only place a lesson is saved.
        return contentService.createLesson(moduleId, request);
    }

    @Override
    @Transactional
    public AdminLessonResponse updateLesson(Long teacherId, Long lessonId, AdminLessonRequest request) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        // A lesson form that does not send video fields must not wipe an existing video, so fall
        // back to what is already stored when the request omits them.
        return contentService.updateLesson(lessonId, withVideoFallback(
                request, lesson.getVideoUrl(), lesson.getDuration()));
    }

    @Override
    @Transactional
    public AdminLessonResponse moveLesson(Long teacherId, Long lessonId, int direction) {
        assertOwnsLesson(teacherId, lessonId);
        return contentService.moveLesson(lessonId, direction);
    }

    @Override
    @Transactional
    public AdminLessonResponse setLessonActive(Long teacherId, Long lessonId, boolean active) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        // Reuse the existing update path and the existing `active` flag - the same flag the Student
        // Portal already honours, so no duplicate status field is introduced.
        AdminLessonResponse updated = contentService.updateLesson(lessonId, new AdminLessonRequest(
                lesson.getTitle(), lesson.getDescription(), lesson.getVideoUrl(), lesson.getDuration(),
                lesson.getDisplayOrder(), active));
        auditLogService.log(active ? "LESSON_ACTIVATED" : "LESSON_DEACTIVATED", "CourseLesson",
            String.valueOf(lessonId), "Changed lesson availability");
        return updated;
    }

    @Override
    @Transactional
    public void deleteLesson(Long teacherId, Long lessonId) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        contentService.deleteLesson(lessonId);
    }

    /* ========================= videos (PART 9C) ========================= */

    @Override
    public AdminLessonResponse video(Long teacherId, Long lessonId) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        return new AdminLessonResponse(
                lesson.getId(), lesson.getModule().getId(), lesson.getTitle(), lesson.getDescription(),
                lesson.getVideoUrl(), lesson.getAudioUrl(), lesson.getDuration(), lesson.getDisplayOrder(), lesson.getActive());
    }

    @Override
    @Transactional
    public AdminLessonResponse setLessonVideo(Long teacherId, Long lessonId, MultipartFile file, String duration) {
        // Full chain re-verified server-side: teacher -> assigned course -> module -> lesson/video.
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a video file.");
        }
        // PART 6A/8: remember what was there before touching anything. If this is a replace (a
        // video already exists) the old file is only ever removed after everything below succeeds.
        String oldVideoUrl = lesson.getVideoUrl();

        // 1) Validate + upload the new video FIRST. uploadVideo() throws BadRequestException on an
        //    invalid file, oversize file, or storage failure - before the lesson is touched at all,
        //    so a failed upload leaves the existing video and videoUrl completely untouched.
        String videoUrl = fileStorageService.uploadVideo(file, "teacher:" + teacherId).url();
        String cleanDuration = duration == null || duration.isBlank() ? null : duration.trim();

        // 2) Persist the new videoUrl. If this throws (e.g. a database failure), it propagates
        //    straight out of this method without reaching step 3 below, so the lesson keeps
        //    pointing at the old video and the new file is simply an orphan on disk - never
        //    referenced, never linked as "the" video for this lesson.
        AdminLessonResponse updated = contentService.updateLesson(lessonId, new AdminLessonRequest(
                lesson.getTitle(), lesson.getDescription(), videoUrl, cleanDuration,
                lesson.getDisplayOrder(), lesson.getActive()));
            auditLogService.log(oldVideoUrl == null ? "MEDIA_UPLOADED" : "MEDIA_REPLACED", "CourseLesson",
                String.valueOf(lessonId), "Video media saved for lesson");

        // 3) Only now, after the lesson row has been switched to the new video, remove the old
        //    local file. deleteVideoIfManaged() is a no-op for anything that isn't a VITC-managed
        //    uploads/videos/ url, so replacing an external URL with a new local upload never
        //    attempts (or needs) a filesystem delete, and this step never touches the new file.
        if (oldVideoUrl != null && !oldVideoUrl.equals(videoUrl)) {
            fileStorageService.deleteVideoIfManaged(oldVideoUrl);
        }

        return updated;
    }

    @Override
    @Transactional
    public AdminLessonResponse clearLessonVideo(Long teacherId, Long lessonId) {
        // Full chain re-verified server-side: teacher -> assigned course -> module -> lesson/video.
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        // PART 6B/8: remember the old url before touching anything, same pattern as replace.
        String oldVideoUrl = lesson.getVideoUrl();

        // 1) Save the DB change first. Only videoUrl is cleared - every other field (including
        //    duration, order, active flag) is passed back exactly as it already is, so nothing
        //    else on the lesson row is touched. If this throws, we never reach step 2, so the old
        //    local file (if any) is left completely intact.
        AdminLessonResponse updated = contentService.updateLesson(lessonId, new AdminLessonRequest(
                lesson.getTitle(), lesson.getDescription(), null, lesson.getDuration(),
                lesson.getDisplayOrder(), lesson.getActive()));

        // 2) Only after the lesson has been saved without a video do we attempt to delete the old
        //    physical file - and only if it was a VITC-managed local upload. deleteVideoIfManaged()
        //    is a no-op for external URLs and swallows any filesystem failure, so a failed cleanup
        //    here can never corrupt or roll back the lesson record that was already saved above.
        if (oldVideoUrl != null && !oldVideoUrl.isBlank()) {
            fileStorageService.deleteVideoIfManaged(oldVideoUrl);
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lessonId),
                    "Deleted video media from lesson");
        }

        return updated;
    }

    @Override
    @Transactional
    public AdminLessonResponse setLessonAudio(Long teacherId, Long lessonId, MultipartFile file) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        String oldAudioUrl = lesson.getAudioUrl();
        String audioUrl = fileStorageService.uploadAudio(file, "teacher:" + teacherId).url();
        lesson.setAudioUrl(audioUrl);
        // The existing lesson write path remains the source of truth; only the new media column changes.
        CourseLesson saved = lesson;
        if (oldAudioUrl != null && !oldAudioUrl.equals(audioUrl)) {
            fileStorageService.deleteFileIfManaged(oldAudioUrl, "audio");
        }
        auditLogService.log(oldAudioUrl == null ? "MEDIA_UPLOADED" : "MEDIA_REPLACED", "CourseLesson",
            String.valueOf(lessonId), "Audio media saved for lesson");
        lessonRepository.save(lesson);
        return new AdminLessonResponse(saved.getId(), saved.getModule().getId(), saved.getTitle(),
                saved.getDescription(), saved.getVideoUrl(), saved.getAudioUrl(), saved.getDuration(),
                saved.getDisplayOrder(), saved.getActive());
    }

    @Override
    @Transactional
    public AdminLessonResponse clearLessonAudio(Long teacherId, Long lessonId) {
        CourseLesson lesson = assertOwnsLesson(teacherId, lessonId);
        String oldAudioUrl = lesson.getAudioUrl();
        lesson.setAudioUrl(null);
        lessonRepository.save(lesson);
        if (oldAudioUrl != null) fileStorageService.deleteFileIfManaged(oldAudioUrl, "audio");
        if (oldAudioUrl != null) {
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lessonId),
                "Deleted audio media from lesson");
        }
        return new AdminLessonResponse(lesson.getId(), lesson.getModule().getId(), lesson.getTitle(),
                lesson.getDescription(), lesson.getVideoUrl(), null, lesson.getDuration(),
                lesson.getDisplayOrder(), lesson.getActive());
    }

    private AdminLessonRequest withVideoFallback(AdminLessonRequest request, String videoUrl, String duration) {
        return new AdminLessonRequest(
                request.title(),
                request.description(),
                request.videoUrl() != null ? request.videoUrl() : videoUrl,
                request.duration() != null ? request.duration() : duration,
                request.displayOrder(),
                request.active());
    }

    /* ========================= authorization (PART 10A) ========================= */

    // Every check below is delegated to the one central TeacherAuthorizationService, which runs
    // the full chain: authenticated -> role TEACHER -> course assigned to that teacher -> module /
    // lesson belongs to that course. Failures throw ForbiddenException -> HTTP 403.

    private Course assertOwnsCourse(Long teacherId, Long courseId) {
        return authorization.requireAssignedCourse(teacherId, courseId);
    }

    private CourseModule assertOwnsModule(Long teacherId, Long moduleId) {
        return authorization.requireOwnedModule(teacherId, moduleId);
    }

    private CourseLesson assertOwnsLesson(Long teacherId, Long lessonId) {
        return authorization.requireOwnedLesson(teacherId, lessonId);
    }
}

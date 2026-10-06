package com.vitc.service;

import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Teacher Admin -> Course Content (PART 9A): module management for the courses assigned to the
 * logged-in teacher.
 *
 * <p>Deliberately thin. It performs the Teacher -> Course -> Module ownership check and then
 * delegates to the existing {@link AdminCourseContentService}, so the {@code course_modules} /
 * {@code course_lessons} tables, entities, repositories and DTOs stay the single source of truth.
 * No {@code teacher_modules} / {@code teacher_lessons} / {@code teacher_videos} architecture is
 * introduced, and Main Admin + Student behaviour is untouched.</p>
 *
 * <p>Every method takes the teacherId resolved server-side from the session
 * ({@code TeacherAuthInterceptor}) - never a client-supplied id - and throws
 * {@link com.vitc.exception.ForbiddenException} when the target course/module is not the teacher's.</p>
 */
public interface TeacherCourseContentService {

    /** Full module (+lesson) tree of an assigned course. */
    AdminCourseContentResponse content(Long teacherId, Long courseId);

    AdminModuleResponse createModule(Long teacherId, Long courseId, AdminModuleRequest request);

    AdminModuleResponse updateModule(Long teacherId, Long moduleId, AdminModuleRequest request);

    /** direction &lt; 0 moves the module up, &gt; 0 moves it down. */
    AdminModuleResponse moveModule(Long teacherId, Long moduleId, int direction);

    /** Flips the module's existing {@code active} flag - no second status field is introduced. */
    AdminModuleResponse setModuleActive(Long teacherId, Long moduleId, boolean active);

    /** PART 1/10 - single module (+its lessons) of an assigned course. */
    AdminModuleResponse module(Long teacherId, Long moduleId);

    /**
     * PART 1/10 - permanently removes a module of an assigned course together with its lessons and
     * their progress rows, through the existing admin delete path. Deactivation stays the default
     * way to hide content; delete is only for genuinely discarding a module.
     */
    void deleteModule(Long teacherId, Long moduleId);

    /* ========================= lessons (PART 9B) ========================= */

    /**
     * Adds a lesson to a module of an assigned course. Since PART 9C the optional video fields
     * (url + duration) of the request are accepted too; the existing {@code course_lessons} row
     * stays the single storage location for both the lesson and its video.
     */
    AdminLessonResponse createLesson(Long teacherId, Long moduleId, AdminLessonRequest request);

    /** Edits title / description / display order / active flag of a lesson the teacher owns. */
    AdminLessonResponse updateLesson(Long teacherId, Long lessonId, AdminLessonRequest request);

    /** direction &lt; 0 moves the lesson up inside its module, &gt; 0 moves it down. */
    AdminLessonResponse moveLesson(Long teacherId, Long lessonId, int direction);

    /** Flips the lesson's existing {@code active} flag - no second status field is introduced. */
    AdminLessonResponse setLessonActive(Long teacherId, Long lessonId, boolean active);

    /** Permanently deletes a lesson of an assigned course and resequences remaining module lessons. */
    void deleteLesson(Long teacherId, Long lessonId);

    /* ========================= videos (PART 9C) ========================= */

    /**
     * Sets / replaces the video of a lesson the teacher owns (existing {@code video_url} and
     * {@code duration} columns). No {@code teacher_videos} table exists: the Student Portal reads
     * the very same lesson row, so the update is visible to authorised students immediately.
     */
    AdminLessonResponse video(Long teacherId, Long lessonId);

    /**
     * PART 2/8 - replaces the JSON {@code videoUrl} write with a multipart upload. The file is
     * stored via {@link com.vitc.service.FileStorageService#uploadVideo}; the resulting public URL
     * is what actually lands in {@code CourseLesson.videoUrl}, so storage stays exactly where it
     * was (that same column) and the Student Portal needs no changes.
     */
    AdminLessonResponse setLessonVideo(Long teacherId, Long lessonId, MultipartFile file, String duration);

    /** Removes the video from a lesson the teacher owns (clears url + duration). */
    AdminLessonResponse clearLessonVideo(Long teacherId, Long lessonId);

    AdminLessonResponse setLessonAudio(Long teacherId, Long lessonId, MultipartFile file);

    AdminLessonResponse clearLessonAudio(Long teacherId, Long lessonId);
}

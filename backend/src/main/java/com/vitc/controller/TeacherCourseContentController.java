package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.service.TeacherCourseContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Teacher Admin -&gt; Course Content (PART 9A). Lives under {@code /api/v1/teacher/**} so the existing
 * {@link TeacherAuthInterceptor} registration guards it - no separate security wiring.
 *
 * <p>The teacher id is only ever the session-resolved {@code teacherId} request attribute; the
 * course/module ids in the path are re-verified server-side by
 * {@link TeacherCourseContentService}, so tampering with them yields 403/404.</p>
 *
 * <p>Reuses the existing module/lesson DTOs and the existing {@code AdminCourseContentService}
 * write path - no duplicate course-content architecture. A "video" is the existing lesson row's
 * {@code video_url} + {@code duration}; no {@code teacher_videos} table exists.</p>
 */
@Tag(name = "Teacher Course Content", description = "Teacher module management, scoped to the logged-in teacher's assigned courses")
@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherCourseContentController {

    private final TeacherCourseContentService service;

    @Operation(summary = "Module/lesson tree of an assigned course (403 if not assigned to you)")
    @GetMapping("/courses/{courseId}/content")
    public ResponseEntity<ApiResponse<AdminCourseContentResponse>> content(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.content(teacherId, courseId)));
    }

    @Operation(summary = "Add a module to an assigned course")
    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> createModule(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId,
            @Valid @RequestBody AdminModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Module created", service.createModule(teacherId, courseId, request)));
    }

    @Operation(summary = "Edit a module of an assigned course")
    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> updateModule(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId,
            @Valid @RequestBody AdminModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Module updated", service.updateModule(teacherId, moduleId, request)));
    }

    @Operation(summary = "Reorder a module (direction = -1 up, 1 down)")
    @PatchMapping("/modules/{moduleId}/move")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> moveModule(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId,
            @RequestParam int direction) {
        return ResponseEntity.ok(ApiResponse.ok("Order updated", service.moveModule(teacherId, moduleId, direction)));
    }

    @Operation(summary = "Activate / deactivate a module (existing `active` flag)")
    @PatchMapping("/modules/{moduleId}/status")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> setModuleActive(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(
                active ? "Module activated" : "Module deactivated",
                service.setModuleActive(teacherId, moduleId, active)));
    }

    @Operation(summary = "View a single module (+ its lessons) of an assigned course")
    @GetMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> module(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId) {
        return ResponseEntity.ok(ApiResponse.ok(service.module(teacherId, moduleId)));
    }

    @Operation(summary = "Delete a module of an assigned course (with its lessons)")
    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteModule(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId) {
        service.deleteModule(teacherId, moduleId);
        return ResponseEntity.ok(ApiResponse.message("Module deleted"));
    }

    /* ========================= lessons (PART 9B) ========================= */

    @Operation(summary = "Add a lesson to a module of an assigned course")
    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> createLesson(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long moduleId,
            @Valid @RequestBody AdminLessonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Lesson created", service.createLesson(teacherId, moduleId, request)));
    }

    @Operation(summary = "Edit a lesson (title, description, order, active) of an assigned course")
    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> updateLesson(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId,
            @Valid @RequestBody AdminLessonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Lesson updated", service.updateLesson(teacherId, lessonId, request)));
    }

    @Operation(summary = "Reorder a lesson inside its module (direction = -1 up, 1 down)")
    @PatchMapping("/lessons/{lessonId}/move")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> moveLesson(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId,
            @RequestParam int direction) {
        return ResponseEntity.ok(ApiResponse.ok("Order updated", service.moveLesson(teacherId, lessonId, direction)));
    }

    @Operation(summary = "Activate / deactivate a lesson (existing `active` flag)")
    @PatchMapping("/lessons/{lessonId}/status")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> setLessonActive(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(
                active ? "Lesson activated" : "Lesson deactivated",
                service.setLessonActive(teacherId, lessonId, active)));
    }

    @Operation(summary = "Delete a lesson of an assigned course")
    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId) {
        service.deleteLesson(teacherId, lessonId);
        return ResponseEntity.ok(ApiResponse.message("Lesson deleted"));
    }

    /* ========================= videos (PART 9C) ========================= */

    @Operation(summary = "Video of a lesson (existing lesson row: url + duration + order + status)")
    @GetMapping("/lessons/{lessonId}/video")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> video(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(ApiResponse.ok(service.video(teacherId, lessonId)));
    }

    @Operation(summary = "Upload / replace the video of a lesson (multipart: file + optional duration)")
    @PutMapping(path = "/lessons/{lessonId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdminLessonResponse>> setVideo(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String duration) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Video saved", service.setLessonVideo(teacherId, lessonId, file, duration)));
    }

    @Operation(summary = "Remove the video from a lesson")
    @DeleteMapping("/lessons/{lessonId}/video")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> clearVideo(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(ApiResponse.ok("Video removed", service.clearLessonVideo(teacherId, lessonId)));
    }

    @Operation(summary = "Upload / replace the audio of a lesson")
    @PutMapping(path = "/lessons/{lessonId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdminLessonResponse>> setAudio(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Audio saved", service.setLessonAudio(teacherId, lessonId, file)));
    }

    @Operation(summary = "Remove the audio from a lesson")
    @DeleteMapping("/lessons/{lessonId}/audio")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> clearAudio(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(ApiResponse.ok("Audio removed", service.clearLessonAudio(teacherId, lessonId)));
    }
}

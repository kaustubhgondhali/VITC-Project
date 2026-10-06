package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.AdminCourseContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin -> Course Content (Part 3): modules and lessons of an existing course.
 * Guarded by AdminAuthInterceptor via the /api/v1/admin/** path pattern.
 */
@Tag(name = "Admin Course Content", description = "Admin management of course modules and lessons")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class AdminCourseContentController {

    private final AdminCourseContentService service;

    @Operation(summary = "Full module/lesson tree of a course")
    @GetMapping("/courses/{courseId}/content")
    public ResponseEntity<ApiResponse<AdminCourseContentResponse>> content(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.content(courseId)));
    }

    /* ---------------- modules ---------------- */

    @Operation(summary = "Add a module")
    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> createModule(@PathVariable Long courseId,
                                                                         @Valid @RequestBody AdminModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Module created", service.createModule(courseId, request)));
    }

    @Operation(summary = "Edit a module")
    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> updateModule(@PathVariable Long moduleId,
                                                                         @Valid @RequestBody AdminModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Module updated", service.updateModule(moduleId, request)));
    }

    @Operation(summary = "Delete a module and its lessons")
    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteModule(@PathVariable Long moduleId) {
        service.deleteModule(moduleId);
        return ResponseEntity.ok(ApiResponse.message("Module deleted"));
    }

    @Operation(summary = "Reorder a module (direction = -1 up, 1 down)")
    @PatchMapping("/modules/{moduleId}/move")
    public ResponseEntity<ApiResponse<AdminModuleResponse>> moveModule(@PathVariable Long moduleId,
                                                                       @RequestParam int direction) {
        return ResponseEntity.ok(ApiResponse.ok("Order updated", service.moveModule(moduleId, direction)));
    }

    /* ---------------- lessons ---------------- */

    @Operation(summary = "Add a lesson to a module")
    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> createLesson(@PathVariable Long moduleId,
                                                                         @Valid @RequestBody AdminLessonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Lesson created", service.createLesson(moduleId, request)));
    }

    @Operation(summary = "Edit a lesson")
    @PutMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> updateLesson(@PathVariable Long lessonId,
                                                                         @Valid @RequestBody AdminLessonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Lesson updated", service.updateLesson(lessonId, request)));
    }

    @Operation(summary = "Delete a lesson")
    @DeleteMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(@PathVariable Long lessonId) {
        service.deleteLesson(lessonId);
        return ResponseEntity.ok(ApiResponse.message("Lesson deleted"));
    }

    @Operation(summary = "Reorder a lesson (direction = -1 up, 1 down)")
    @PatchMapping("/lessons/{lessonId}/move")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> moveLesson(@PathVariable Long lessonId,
                                                                       @RequestParam int direction) {
        return ResponseEntity.ok(ApiResponse.ok("Order updated", service.moveLesson(lessonId, direction)));
    }

    @PutMapping(path = "/lessons/{lessonId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdminLessonResponse>> setAudio(@PathVariable Long lessonId,
                                                                      @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Audio saved", service.setLessonAudio(lessonId, file)));
    }

    @DeleteMapping("/lessons/{lessonId}/audio")
    public ResponseEntity<ApiResponse<AdminLessonResponse>> clearAudio(@PathVariable Long lessonId) {
        return ResponseEntity.ok(ApiResponse.ok("Audio removed", service.clearLessonAudio(lessonId)));
    }
}

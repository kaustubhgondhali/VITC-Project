package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.LessonProgressRequest;
import com.vitc.dto.response.StudentCourseDetailResponse;
import com.vitc.dto.response.StudentCourseSummaryResponse;
import com.vitc.dto.response.StudentLessonDetailResponse;
import com.vitc.dto.response.StudentModuleResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.security.StudentAuthInterceptor;
import com.vitc.service.StudentLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student learning portal API. Every endpoint sits behind
 * {@link StudentAuthInterceptor} (session token -> studentId request
 * attribute), and the service re-verifies the enrolment for the requested
 * course, so a student id or course id from the browser is never trusted.
 */
@Tag(name = "Student Learning", description = "Enrolled courses, modules, video lessons and progress")
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@RequireRole(Role.STUDENT)
public class StudentLearningController {

    private final StudentLearningService service;

    @Operation(summary = "Courses the authenticated student is enrolled in, with progress")
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<StudentCourseSummaryResponse>>> courses(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.myCourses(studentId)));
    }

    @Operation(summary = "One enrolled course with its modules and lessons (403 when not enrolled)")
    @GetMapping("/courses/{courseId}")
    public ResponseEntity<ApiResponse<StudentCourseDetailResponse>> course(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.course(studentId, courseId)));
    }

    @Operation(summary = "Modules and lessons of an enrolled course (403 when not enrolled)")
    @GetMapping("/courses/{courseId}/modules")
    public ResponseEntity<ApiResponse<List<StudentModuleResponse>>> modules(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.modules(studentId, courseId)));
    }

    @Operation(summary = "Authorised lesson with its video url (403 when the course is not owned)")
    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<ApiResponse<StudentLessonDetailResponse>> lesson(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @PathVariable Long lessonId) {
        return ResponseEntity.ok(ApiResponse.ok(service.lesson(studentId, lessonId)));
    }

    @Operation(summary = "Save lesson progress / mark a lesson complete")
    @PostMapping("/lessons/{lessonId}/progress")
    public ResponseEntity<ApiResponse<StudentLessonDetailResponse>> saveProgress(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @PathVariable Long lessonId,
            @Valid @RequestBody(required = false) LessonProgressRequest request) {
        LessonProgressRequest body = request == null ? new LessonProgressRequest(null, null) : request;
        return ResponseEntity.ok(ApiResponse.ok("Progress saved", service.saveProgress(studentId, lessonId, body)));
    }
}

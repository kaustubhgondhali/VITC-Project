package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.TeacherCourseInfoRequest;
import com.vitc.dto.response.CourseResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.service.TeacherCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher Admin -> "My Courses" (PART 8B). Lives under {@code /api/v1/teacher/**}, so it is
 * guarded by the same {@link TeacherAuthInterceptor} registration in {@code WebConfig} as every
 * other teacher endpoint - no separate security wiring needed.
 *
 * <p>Reuses the existing {@code courses} table/entity/repository/mapper (see
 * {@code TeacherCourseService}) - no duplicate course-content architecture is introduced.</p>
 *
 * <p>The teacher's identity always comes from the server-side session (the {@code teacherId}
 * request attribute published by {@link TeacherAuthInterceptor}), never from a client-supplied id
 * or query parameter - so a teacher can only ever list/open their own assigned courses.</p>
 */
@Tag(name = "Teacher Courses", description = "Teacher Admin \"My Courses\" - scoped to the logged-in teacher's assigned courses only")
@RestController
@RequestMapping("/api/v1/teacher/courses")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherCourseController {

    private final TeacherCourseService service;

    @Operation(summary = "List the courses assigned to the logged-in teacher")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> myCourses(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(service.myCourses(teacherId)));
    }

    @Operation(summary = "A single assigned course, for the Manage Content screen. "
            + "Rejects the request (403) if the course exists but is not assigned to this teacher.")
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseResponse>> myCourseById(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.myCourseById(teacherId, courseId)));
    }

    @Operation(summary = "Edit the title/description of an assigned course (PART 3/8). "
            + "Code, price, category and active flag stay Main-Admin-only and are left untouched.")
    @PutMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourseInfo(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId,
            @Valid @RequestBody TeacherCourseInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Course updated", service.updateCourseInfo(teacherId, courseId, request)));
    }
}

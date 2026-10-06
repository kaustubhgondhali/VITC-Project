package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.TeacherLessonProgressResponse;
import com.vitc.dto.response.TeacherStudentProgressResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.service.TeacherStudentProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PART 11B-3 - Teacher view of student progress, strictly scoped to courses assigned to the
 * logged-in teacher ({@code Course.teacherId}). Lives under {@code /api/v1/teacher/**} so the
 * existing {@link TeacherAuthInterceptor} guards it, and is additionally opted into the
 * centralized {@link RequireRole} check from PART 11B-1.
 *
 * <p>Neither the course id nor the student id in the path is trusted: both are re-verified
 * server-side by {@code TeacherStudentProgressService} on every call - a hand-edited course id
 * belonging to another teacher, or a student id who was never enrolled in the given course,
 * is rejected with 403 Forbidden before any progress data is read.</p>
 */
@Tag(name = "Teacher Student Progress", description = "Student progress within the logged-in teacher's own assigned courses")
@RestController
@RequestMapping("/api/v1/teacher/courses/{courseId}/students")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherStudentProgressController {

    private final TeacherStudentProgressService service;

    @Operation(summary = "Every enrolled student's progress summary for one of my own courses")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TeacherStudentProgressResponse>>> courseStudents(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.courseStudents(teacherId, courseId)));
    }

    @Operation(summary = "Per-lesson progress detail for one student in one of my own courses")
    @GetMapping("/{studentId}/progress")
    public ResponseEntity<ApiResponse<List<TeacherLessonProgressResponse>>> studentProgress(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @PathVariable Long courseId,
            @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.studentProgress(teacherId, courseId, studentId)));
    }
}

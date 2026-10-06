package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.TeacherStudentRosterResponse;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher Admin -&gt; "Students" (PART 4/8). Lives under {@code /api/v1/teacher/**} so it is
 * guarded by the existing {@link TeacherAuthInterceptor} registration in {@code WebConfig} - no
 * separate security wiring needed - and is additionally opted into the centralized
 * {@link RequireRole} check.
 *
 * <p>The existing {@code TeacherStudentProgressController} already lists the students of one
 * course at a time ({@code GET /teacher/courses/{courseId}/students}); this is the one genuinely
 * missing piece - every student across every course the teacher is assigned to, in a single call,
 * for the dedicated Students page. It is built entirely on {@link TeacherStudentProgressService},
 * which in turn reuses the existing {@code Enrollment}/{@code User}/{@code Course}/
 * {@code StudentLessonProgress} data - no new enrollment or student model.</p>
 */
@Tag(name = "Teacher Students", description = "Every student enrolled in the logged-in teacher's assigned courses")
@RestController
@RequestMapping("/api/v1/teacher/students")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherStudentController {

    private final TeacherStudentProgressService service;

    @Operation(summary = "Every student enrolled in any of the logged-in teacher's assigned courses")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TeacherStudentRosterResponse>>> myStudents(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(service.myStudents(teacherId)));
    }
}

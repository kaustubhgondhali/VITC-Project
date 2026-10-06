package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.TeacherDashboardStatsResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.service.TeacherDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher Admin dashboard. Lives under the same {@code /api/v1/teacher/**}
 * base path as {@link TeacherController}, so it is guarded by the exact
 * same {@link TeacherAuthInterceptor} registration in {@code WebConfig} -
 * no separate security wiring needed. Split into its own controller,
 * mirroring how Main Admin keeps auth ({@code AdminController}) separate
 * from content-heavy concerns ({@code AdminCourseContentController},
 * {@code AdminStudentController}).
 *
 * <p>The teacher's identity always comes from the server-side session
 * (the {@code teacherId} request attribute published by
 * {@link TeacherAuthInterceptor}), never from a client-supplied id - so a
 * teacher can only ever see their own dashboard.</p>
 */
@Tag(name = "Teacher Dashboard", description = "Teacher Admin dashboard statistics, scoped to the logged-in teacher's own courses")
@RestController
@RequestMapping("/api/v1/teacher/dashboard")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherDashboardController {

    private final TeacherDashboardService service;

    @Operation(summary = "Dashboard statistics for the logged-in teacher's own courses")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<TeacherDashboardStatsResponse>> stats(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(service.dashboardStats(teacherId)));
    }
}

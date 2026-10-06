package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AdminEnrollmentRequest;
import com.vitc.dto.response.AdminStudentCourseResponse;
import com.vitc.dto.response.AdminStudentDetailResponse;
import com.vitc.dto.response.AdminStudentResponse;
import com.vitc.dto.response.UserResponse;
import com.vitc.entity.enums.UserStatus;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.AdminStudentService;
import com.vitc.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin -> Students (Part 3). Everything under /api/v1/admin/** is guarded by
 * the existing AdminAuthInterceptor, so a student session token can never
 * reach these endpoints.
 */
@Tag(name = "Admin Students", description = "Admin management of student accounts")
@RestController
@RequestMapping("/api/v1/admin/students")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class AdminStudentController {

    private final AdminStudentService service;
    private final UserService userService;

    @Operation(summary = "List student accounts")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminStudentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @Operation(summary = "Student details with enrolments and progress")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminStudentDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.detail(id)));
    }

    @Operation(summary = "Courses a student is enrolled in")
    @GetMapping("/{id}/courses")
    public ResponseEntity<ApiResponse<List<AdminStudentCourseResponse>>> courses(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.courses(id)));
    }

    @Operation(summary = "Per-course progress of a student")
    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<List<AdminStudentCourseResponse>>> progress(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.courses(id)));
    }

    @Operation(summary = "Activate / deactivate / block a student (existing user status model)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> status(@PathVariable Long id,
                                                            @RequestParam UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", userService.updateStatus(id, status)));
    }

    @Operation(summary = "Manually grant a course (no payment record is created or altered)")
    @PostMapping("/{id}/enrollments")
    public ResponseEntity<ApiResponse<AdminStudentDetailResponse>> grant(@PathVariable Long id,
                                                                         @Valid @RequestBody AdminEnrollmentRequest request,
                                                                         HttpServletRequest http) {
        String admin = http.getHeader("X-Admin-Username");
        return ResponseEntity.ok(ApiResponse.ok("Course access granted",
                service.grantEnrollment(id, request, admin)));
    }
}

package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AdminTeacherRequest;
import com.vitc.dto.request.AdminTeacherUpdateRequest;
import com.vitc.dto.request.TeacherCourseAssignmentRequest;
import com.vitc.dto.request.TeacherPasswordResetRequest;
import com.vitc.dto.response.AdminTeacherCourseResponse;
import com.vitc.dto.response.AdminTeacherResponse;
import com.vitc.dto.response.TeacherCredentialsResponse;
import com.vitc.entity.enums.UserStatus;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.AdminTeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

/**
 * PART 10C - Main Admin -&gt; Teachers, a section of the EXISTING Admin Panel.
 * Everything lives under {@code /api/v1/admin/**}, which the existing
 * {@code AdminAuthInterceptor} already guards, so a Teacher or Student session
 * token is rejected with 403 Forbidden before any handler runs (PART 10B).
 */
@Tag(name = "Admin Teachers", description = "Main Admin management of teacher accounts and course assignments")
@RestController
@RequestMapping("/api/v1/admin/teachers")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class AdminTeacherController {

    private final AdminTeacherService service;

    @Operation(summary = "List teacher accounts with their assigned courses")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminTeacherResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @Operation(summary = "Every course with its current teacher assignment")
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<AdminTeacherCourseResponse>>> courses() {
        return ResponseEntity.ok(ApiResponse.ok(service.allCourses()));
    }

    @Operation(summary = "Teacher details")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminTeacherResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    @Operation(summary = "Courses assigned to a teacher")
    @GetMapping("/{id}/courses")
    public ResponseEntity<ApiResponse<List<AdminTeacherCourseResponse>>> assigned(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.assignedCourses(id)));
    }

    @Operation(summary = "Add a teacher (temporary password returned once)")
    @PostMapping
    public ResponseEntity<ApiResponse<TeacherCredentialsResponse>> create(
            @Valid @RequestBody AdminTeacherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Teacher created", service.create(request)));
    }

    @Operation(summary = "Edit teacher profile")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminTeacherResponse>> update(
            @PathVariable Long id, @Valid @RequestBody AdminTeacherUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Teacher updated", service.update(id, request)));
    }

    @Operation(summary = "Activate / deactivate a teacher (deactivation denies login and all teacher APIs)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminTeacherResponse>> status(@PathVariable Long id,
                                                                    @RequestParam UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, status)));
    }

    @Operation(summary = "Replace the teacher's course assignments")
    @PutMapping("/{id}/courses")
    public ResponseEntity<ApiResponse<AdminTeacherResponse>> assign(
            @PathVariable Long id, @RequestBody TeacherCourseAssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Course assignments updated", service.assignCourses(id, request)));
    }

    @Operation(summary = "Remove one course assignment (course content is preserved)")
    @DeleteMapping("/{id}/courses/{courseId}")
    public ResponseEntity<ApiResponse<AdminTeacherResponse>> removeCourse(@PathVariable Long id,
                                                                          @PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok("Course assignment removed", service.removeCourse(id, courseId)));
    }

    @Operation(summary = "Reset a teacher password (hash only; plain value returned once)")
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<ApiResponse<TeacherCredentialsResponse>> resetPassword(
            @PathVariable Long id, @RequestBody(required = false) TeacherPasswordResetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Password reset", service.resetPassword(id, request)));
    }
}

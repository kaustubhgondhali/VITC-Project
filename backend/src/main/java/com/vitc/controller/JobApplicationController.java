package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.JobApplicationRequest;
import com.vitc.dto.response.JobApplicationResponse;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.service.JobApplicationService;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Careers", description = "CRUD operations for job applications")
@RestController
@RequestMapping("/api/v1/careers")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService service;

    @Operation(summary = "List all records")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobApplicationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @PostMapping
    public ResponseEntity<ApiResponse<JobApplicationResponse>> create(@Valid @RequestBody JobApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody JobApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List records by status")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<JobApplicationResponse>>> byStatus(
            @PathVariable ApplicationStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByStatus(status)));
    }

    @Operation(summary = "Update the status")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> updateStatus(
            @PathVariable Long id, @RequestParam ApplicationStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, status)));
    }
}

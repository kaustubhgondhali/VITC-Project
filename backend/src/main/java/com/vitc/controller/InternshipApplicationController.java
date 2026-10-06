package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.InternshipApplicationRequest;
import com.vitc.dto.response.InternshipApplicationResponse;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.service.InternshipApplicationService;
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

@Tag(name = "Internships", description = "CRUD operations for internship applications")
@RestController
@RequestMapping("/api/v1/internships")
@RequiredArgsConstructor
public class InternshipApplicationController {

    private final InternshipApplicationService service;

    @Operation(summary = "List all records")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<InternshipApplicationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InternshipApplicationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @PostMapping
    public ResponseEntity<ApiResponse<InternshipApplicationResponse>> create(@Valid @RequestBody InternshipApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InternshipApplicationResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody InternshipApplicationRequest request) {
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
    public ResponseEntity<ApiResponse<List<InternshipApplicationResponse>>> byStatus(
            @PathVariable ApplicationStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByStatus(status)));
    }

    @Operation(summary = "Update the status")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<InternshipApplicationResponse>> updateStatus(
            @PathVariable Long id, @RequestParam ApplicationStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, status)));
    }
}

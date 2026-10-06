package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.EnrollmentRequest;
import com.vitc.dto.request.EnrollmentStatusRequest;
import com.vitc.dto.response.EnrollmentResponse;
import com.vitc.service.EnrollmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Enrollments", description = "CRUD operations for course enrollments")
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService service;

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @PostMapping
    public ResponseEntity<ApiResponse<EnrollmentResponse>> create(@Valid @RequestBody EnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Enrollment created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> update(@PathVariable Long id,
                                                                  @Valid @RequestBody EnrollmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Enrollment updated", service.update(id, request)));
    }

    @Operation(summary = "Update the status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody EnrollmentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, request.status())));
    }

    @Operation(summary = "List records by email")
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> byEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByEmail(email)));
    }

    @Operation(summary = "By course")
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> byCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCourse(courseId)));
    }

    @Operation(summary = "Delete a record")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Enrollment deleted"));
    }
}

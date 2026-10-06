package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AssignmentRequest;
import com.vitc.dto.response.AssignmentResponse;
import com.vitc.service.AssignmentService;
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

@Tag(name = "Assignments", description = "CRUD operations for assignments")
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService service;

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(@Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List active records")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.ok(service.getActive()));
    }

    @Operation(summary = "Get a record by code")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> byCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCode(code)));
    }

    @Operation(summary = "List records by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategory(category)));
    }
}

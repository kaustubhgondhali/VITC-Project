package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.TestimonialRequest;
import com.vitc.dto.response.TestimonialResponse;
import com.vitc.service.TestimonialService;
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

@Tag(name = "Testimonials", description = "CRUD operations for testimonials")
@RestController
@RequestMapping("/api/v1/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialService service;

    @Operation(summary = "List all records")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<TestimonialResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TestimonialResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<TestimonialResponse>> create(@Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TestimonialResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List approved records")
    @GetMapping("/approved")
    public ResponseEntity<ApiResponse<List<TestimonialResponse>>> approved() {
        return ResponseEntity.ok(ApiResponse.ok(service.getApproved()));
    }

    @Operation(summary = "Update the approval flag")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/approval")
    public ResponseEntity<ApiResponse<TestimonialResponse>> approval(@PathVariable Long id,
                                                                    @RequestParam boolean approved) {
        return ResponseEntity.ok(ApiResponse.ok("Approval updated", service.setApproved(id, approved)));
    }
}

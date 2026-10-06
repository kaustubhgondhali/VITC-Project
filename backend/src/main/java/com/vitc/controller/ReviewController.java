package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.common.PageResponse;
import com.vitc.dto.request.ReviewRequest;
import com.vitc.dto.response.ReviewResponse;
import com.vitc.service.ReviewService;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reviews", description = "CRUD operations for student course reviews")
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService service;

    @Operation(summary = "List all reviews (admin view)")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "List approved reviews with pagination")
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> paged(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getApprovedPaged(page, size)));
    }

    @Operation(summary = "Get a review by id")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Review found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Review not found")
    })
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Submit a review")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Review submitted successfully", service.create(request)));
    }

    @Operation(summary = "Update a review")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(@PathVariable Long id,
                                                              @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Approve or reject a review")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/approval")
    public ResponseEntity<ApiResponse<ReviewResponse>> approval(@PathVariable Long id,
                                                                @RequestParam boolean approved) {
        return ResponseEntity.ok(ApiResponse.ok("Approval updated", service.updateApproval(id, approved)));
    }

    @Operation(summary = "Delete a review")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List approved reviews")
    @GetMapping("/approved")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> approved() {
        return ResponseEntity.ok(ApiResponse.ok(service.getApproved()));
    }

    @Operation(summary = "List featured reviews")
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> featured() {
        return ResponseEntity.ok(ApiResponse.ok(service.getFeatured()));
    }

    @Operation(summary = "List reviews for a course")
    @GetMapping("/course/{courseCode}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> byCourse(@PathVariable String courseCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCourseCode(courseCode)));
    }

    @Operation(summary = "List reviews submitted by an email")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> byEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByEmail(email)));
    }

    @Operation(summary = "Average approved rating for a course")
    @GetMapping("/course/{courseCode}/average-rating")
    public ResponseEntity<ApiResponse<Double>> averageRating(@PathVariable String courseCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.averageRating(courseCode)));
    }
}

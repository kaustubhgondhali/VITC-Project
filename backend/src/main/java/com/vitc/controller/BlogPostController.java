package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.BlogPostRequest;
import com.vitc.dto.response.BlogPostResponse;
import com.vitc.service.BlogPostService;
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

@Tag(name = "Blog", description = "CRUD operations for blog posts")
@RestController
@RequestMapping("/api/v1/blog-posts")
@RequiredArgsConstructor
public class BlogPostController {

    private final BlogPostService service;

    @Operation(summary = "List all records")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<BlogPostResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<BlogPostResponse>> create(@Valid @RequestBody BlogPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody BlogPostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "Published")
    @GetMapping("/published")
    public ResponseEntity<ApiResponse<List<BlogPostResponse>>> published() {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished()));
    }

    @Operation(summary = "By slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BlogPostResponse>> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(service.getBySlug(slug)));
    }
}

package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.GalleryCategoryRequest;
import com.vitc.dto.request.GalleryItemRequest;
import com.vitc.dto.response.GalleryItemResponse;
import com.vitc.service.GalleryCategoryService;
import com.vitc.service.GalleryItemService;
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

@Tag(name = "Gallery", description = "CRUD operations for gallery items")
@RestController
@RequestMapping("/api/v1/gallery")
@RequiredArgsConstructor
public class GalleryItemController {

    private final GalleryItemService service;
    private final GalleryCategoryService categoryService;

    @Operation(summary = "List gallery categories")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAll()));
    }

    @Operation(summary = "Create a gallery category")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<String>> createCategory(@Valid @RequestBody GalleryCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Category created successfully", categoryService.create(request)));
    }

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GalleryItemResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GalleryItemResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<GalleryItemResponse>> create(@Valid @RequestBody GalleryItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GalleryItemResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody GalleryItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List records by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<GalleryItemResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategory(category)));
    }
}

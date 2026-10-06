package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.CourseRequest;
import com.vitc.dto.response.CourseResponse;
import com.vitc.service.CourseService;
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

@Tag(name = "Courses", description = "CRUD operations for courses")
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService service;

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    /*
     * PART 2/6 - VITC course-visibility fix.
     *
     * getActive(), byCode() and byCategory() below are declared before
     * getById(Long) on purpose (Spring resolves the literal "/active" path
     * ahead of the "/{id}" variable path regardless of declaration order,
     * but keeping the literal routes first here documents that intent and
     * prevents a future edit from accidentally relying on declaration
     * order). GET /api/v1/courses/active always hits getActive(), never
     * getById() with id="active".
     */
    @Operation(summary = "List active records")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.ok(service.getActive()));
    }

    @Operation(summary = "Get a record by code")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CourseResponse>> byCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCode(code)));
    }

    @Operation(summary = "List records by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategory(category)));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }
}

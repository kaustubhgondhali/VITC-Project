package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.common.PageResponse;
import com.vitc.dto.request.PasswordChangeRequest;
import com.vitc.dto.request.UserRequest;
import com.vitc.dto.request.UserUpdateRequest;
import com.vitc.dto.response.UserResponse;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.UserService;
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

@Tag(name = "Users", description = "CRUD operations for user accounts")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class UserController {

    private final UserService service;

    @Operation(summary = "List all users")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Search users by name or email with pagination")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @Parameter(description = "Name or email fragment") @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(keyword, page, size)));
    }

    @Operation(summary = "Get a user by id")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Get a user by email")
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserResponse>> byEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByEmail(email)));
    }

    @Operation(summary = "Create a user")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a user profile")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Update a user status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(@PathVariable Long id,
                                                                  @RequestParam UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, status)));
    }

    @Operation(summary = "Change a user password")
    @PatchMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Long id,
                                                            @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(id, request);
        return ResponseEntity.ok(ApiResponse.message("Password updated successfully"));
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List users by role")
    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<UserResponse>>> byRole(@PathVariable UserRole role) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByRole(role)));
    }

    @Operation(summary = "List users by status")
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<UserResponse>>> byStatus(@PathVariable UserStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByStatus(status)));
    }
}

package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.ContactMessageRequest;
import com.vitc.dto.response.ContactMessageResponse;
import com.vitc.service.ContactMessageService;
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

@Tag(name = "Contact", description = "CRUD operations for contact messages")
@RestController
@RequestMapping("/api/v1/contact-messages")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageService service;

    @Operation(summary = "List all records")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactMessageResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a record")
    @PostMapping
    public ResponseEntity<ApiResponse<ContactMessageResponse>> create(@Valid @RequestBody ContactMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody ContactMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a record")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "Unhandled")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/unhandled")
    public ResponseEntity<ApiResponse<List<ContactMessageResponse>>> unhandled() {
        return ResponseEntity.ok(ApiResponse.ok(service.getUnhandled()));
    }

    @Operation(summary = "Mark handled")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/handled")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markHandled(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Message marked as handled", service.markHandled(id)));
    }
}

package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.FaqRequest;
import com.vitc.dto.response.FaqResponse;
import com.vitc.service.FaqService;
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

@Tag(name = "FAQ", description = "CRUD operations for frequently asked questions")
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService service;

    @Operation(summary = "List all FAQs")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "FAQs returned"))
    @GetMapping
    public ResponseEntity<ApiResponse<List<FaqResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a FAQ by id")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "FAQ found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "FAQ not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqResponse>> getById(@Parameter(description = "FAQ id") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a FAQ")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "FAQ created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate question")
    })
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<FaqResponse>> create(@Valid @RequestBody FaqRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a FAQ")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FaqResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody FaqRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Toggle FAQ visibility")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<FaqResponse>> updateActive(@PathVariable Long id,
                                                                 @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateActive(id, active)));
    }

    @Operation(summary = "Delete a FAQ")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List active FAQs ordered by display order")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.ok(service.getActive()));
    }

    @Operation(summary = "List FAQs by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategory(category)));
    }

    @Operation(summary = "Search FAQs by keyword")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FaqResponse>>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(keyword)));
    }
}

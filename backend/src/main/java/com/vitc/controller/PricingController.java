package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.PricingPlanRequest;
import com.vitc.dto.response.PricingPlanResponse;
import com.vitc.service.PricingPlanService;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Pricing", description = "CRUD operations for pricing plans")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingPlanService service;

    @Operation(summary = "List all pricing plans")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingPlanResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "List active pricing plans")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PricingPlanResponse>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(service.getActive()));
    }

    @Operation(summary = "List pricing plans by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<PricingPlanResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategory(category)));
    }

    @Operation(summary = "Get a pricing plan by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingPlanResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a pricing plan")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<PricingPlanResponse>> create(@Valid @RequestBody PricingPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update a pricing plan")
    @RequireRole(Role.MAIN_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingPlanResponse>> update(@PathVariable Long id,
                                                                   @Valid @RequestBody PricingPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Activate or deactivate a pricing plan")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<PricingPlanResponse>> updateActive(@PathVariable Long id,
                                                                         @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateActive(id, active)));
    }

    @Operation(summary = "Delete a pricing plan")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }
}

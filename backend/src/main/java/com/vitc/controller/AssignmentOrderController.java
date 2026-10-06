package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AssignmentOrderRequest;
import com.vitc.dto.request.OrderStatusRequest;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.service.AssignmentOrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orders", description = "CRUD operations for assignment orders")
@RestController
@RequestMapping("/api/v1/assignment-orders")
@RequiredArgsConstructor
public class AssignmentOrderController {

    private final AssignmentOrderService service;

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentOrderResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Get a record by code")
    @GetMapping("/code/{orderCode}")
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> byCode(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByOrderCode(orderCode)));
    }

    @Operation(summary = "Create a record")
    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> create(
            @Valid @RequestBody AssignmentOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order placed successfully", service.create(request)));
    }

    @Operation(summary = "Update a record")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> update(
            @PathVariable Long id, @Valid @RequestBody AssignmentOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Order updated", service.update(id, request)));
    }

    @Operation(summary = "Update the status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AssignmentOrderResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateStatus(id, request.status())));
    }

    @Operation(summary = "List records by email")
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<List<AssignmentOrderResponse>>> byEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByEmail(email)));
    }

    @Operation(summary = "Delete a record")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Order deleted"));
    }
}

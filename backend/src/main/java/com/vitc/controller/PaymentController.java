package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.PaymentRequest;
import com.vitc.dto.request.PaymentStatusRequest;
import com.vitc.dto.response.PaymentResponse;
import com.vitc.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "CRUD operations for payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @Operation(summary = "List all records")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get a record by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Get a payment by transaction id")
    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> byTransaction(@PathVariable String transactionId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByTransactionId(transactionId)));
    }

    @Operation(summary = "Initiate a payment")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Payment initiated", service.initiate(request)));
    }

    @Operation(summary = "Update an unsettled payment")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a payment")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "Update the status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PaymentResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody PaymentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment status updated", service.updateStatus(id, request.status())));
    }

    @Operation(summary = "List records by email")
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> byEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByEmail(email)));
    }
}

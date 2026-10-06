package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.CheckoutOrderRequest;
import com.vitc.dto.request.PaymentConfirmRequest;
import com.vitc.dto.request.PaymentInitiateRequest;
import com.vitc.dto.response.OrderConfirmationResponse;
import com.vitc.dto.response.PaymentInitiationResponse;
import com.vitc.dto.response.PaymentOrderResponse;
import com.vitc.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Checkout", description = "Order, payment and confirmation flow")
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService service;

    @Operation(summary = "List all checkout orders")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<PaymentOrderResponse>>> getAll(
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(ApiResponse.ok(
                email == null ? service.getAllOrders() : service.getOrdersByEmail(email)));
    }

    @Operation(summary = "Create a checkout order (totals are computed server-side)")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> create(@Valid @RequestBody CheckoutOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order created", service.createOrder(request)));
    }

    @Operation(summary = "Get an order summary by order code")
    @GetMapping("/orders/{orderCode}")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> get(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getOrder(orderCode)));
    }

    @Operation(summary = "Start a payment attempt for an order (UPI, CARD, WALLET, NETBANKING)")
    @PostMapping("/orders/{orderCode}/pay")
    public ResponseEntity<ApiResponse<PaymentInitiationResponse>> pay(@PathVariable String orderCode,
                                                                     @Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment initiated", service.initiatePayment(orderCode, request)));
    }

    @Operation(summary = "Confirm / verify a payment and issue the invoice")
    @PostMapping("/orders/{orderCode}/confirm")
    public ResponseEntity<ApiResponse<OrderConfirmationResponse>> confirm(@PathVariable String orderCode,
                                                                         @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment successful", service.confirmPayment(orderCode, request)));
    }

    @Operation(summary = "Get the confirmation bundle (order + payment + invoice)")
    @GetMapping("/orders/{orderCode}/confirmation")
    public ResponseEntity<ApiResponse<OrderConfirmationResponse>> confirmation(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getConfirmation(orderCode)));
    }

    @Operation(summary = "Cancel an unpaid order")
    @PostMapping("/orders/{orderCode}/cancel")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> cancel(@PathVariable String orderCode) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled", service.cancelOrder(orderCode)));
    }
}

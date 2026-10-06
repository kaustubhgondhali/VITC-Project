package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.PaymentSettingsRequest;
import com.vitc.dto.response.ConnectionTestResponse;
import com.vitc.dto.response.PaymentSettingsResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.PaymentSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only payment gateway configuration.
 *
 * <p>Protected by {@code AdminAuthInterceptor} (registered in
 * {@code WebConfig}) — the same session token the admin panel already uses.
 * The Razorpay key secret is never returned by any endpoint here.</p>
 */
@Tag(name = "Admin Payment Settings", description = "Configure the site owner's own payment gateway")
@RestController
@RequestMapping("/api/v1/admin/payment-settings")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class PaymentSettingsController {

    private final PaymentSettingsService service;

    @Operation(summary = "Get the current payment configuration (secret is never returned)")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.getSettings()));
    }

    @Operation(summary = "Save the payment configuration (secret is encrypted before storage)")
    @PutMapping
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> save(
            @Valid @RequestBody PaymentSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Payment settings saved", service.save(request)));
    }

    @Operation(summary = "Save the payment configuration (alias of PUT)")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> saveViaPost(
            @Valid @RequestBody PaymentSettingsRequest request) {
        return save(request);
    }

    @Operation(summary = "Test the configured Razorpay credentials")
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<ConnectionTestResponse>> test() {
        ConnectionTestResponse result = service.testConnection();
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
    }

    @Operation(summary = "Enable Razorpay for live checkout")
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> enable() {
        return ResponseEntity.ok(ApiResponse.ok("Razorpay enabled", service.setEnabled(true)));
    }

    @Operation(summary = "Disable Razorpay (checkout falls back to the mock gateway)")
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> disable() {
        return ResponseEntity.ok(ApiResponse.ok("Razorpay disabled", service.setEnabled(false)));
    }
}

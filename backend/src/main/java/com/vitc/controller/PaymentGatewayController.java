package com.vitc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.common.ApiResponse;
import com.vitc.dto.response.GatewayStatusResponse;
import com.vitc.payment.gateway.RazorpayClient;
import com.vitc.service.CheckoutService;
import com.vitc.service.PaymentSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public payment endpoints: the credential-free gateway status the storefront
 * reads before checkout, and the Razorpay webhook.
 */
@Slf4j
@Tag(name = "Payment Gateway", description = "Gateway status and Razorpay webhook")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentGatewayController {

    private final PaymentSettingsService settingsService;
    private final CheckoutService checkoutService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Which gateway the checkout will use (no credentials returned)")
    @GetMapping("/gateway")
    public ResponseEntity<ApiResponse<GatewayStatusResponse>> gateway() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getPublicStatus()));
    }

    /**
     * Razorpay webhook. The payload is only trusted after its HMAC-SHA256
     * signature validates against the configured webhook secret; processing is
     * idempotent, so replays never create duplicate payments or orders.
     */
    @Operation(summary = "Razorpay webhook (signature verified server-side)")
    @PostMapping("/razorpay/webhook")
    public ResponseEntity<String> webhook(@RequestBody String rawBody,
                                          @RequestHeader(value = "X-Razorpay-Signature", required = false)
                                          String signature) {
        String webhookSecret;
        try {
            webhookSecret = settingsService.razorpayCredentials().webhookSecret();
        } catch (Exception e) {
            log.warn("Razorpay webhook received but the gateway is not configured");
            return ResponseEntity.status(400).body("not configured");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Razorpay webhook received but no webhook secret is configured");
            return ResponseEntity.status(400).body("webhook secret not configured");
        }
        if (signature == null
                || !RazorpayClient.signaturesMatch(
                        RazorpayClient.hmacSha256Hex(rawBody, webhookSecret), signature)) {
            log.warn("Rejected Razorpay webhook with an invalid signature");
            return ResponseEntity.status(401).body("invalid signature");
        }

        try {
            JsonNode event = mapper.readTree(rawBody);
            String type = event.path("event").asText("");
            if ("payment.captured".equals(type) || "order.paid".equals(type)) {
                JsonNode payment = event.path("payload").path("payment").path("entity");
                String orderId = payment.path("order_id").asText(null);
                String paymentId = payment.path("id").asText(null);
                if (orderId != null && paymentId != null) {
                    checkoutService.settleVerifiedWebhookPayment(orderId, paymentId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook: {}", e.toString());
            return ResponseEntity.status(500).body("processing error");
        }
        return ResponseEntity.ok("ok");
    }
}

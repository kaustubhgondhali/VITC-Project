package com.vitc.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.vitc.exception.BadRequestException;
import com.vitc.service.PaymentSettingsService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Real Razorpay adapter. Credentials come from the merchant configuration the
 * site owner saves in Admin -> Payment Settings (encrypted at rest), never from
 * source code.
 *
 * <p>Selected at runtime by {@link PaymentGatewayRouter} when Razorpay is
 * configured and enabled.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "RAZORPAY";

    private final PaymentSettingsService settingsService;
    private final RazorpayClient client;

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public GatewayOrder createOrder(GatewayOrderRequest request) {
        var creds = settingsService.razorpayCredentials();
        JsonNode order = client.createOrder(creds.keyId(), creds.keySecret(),
                request.orderCode(), request.amount(), request.currency());
        String providerOrderId = order.path("id").asText(null);
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw new BadRequestException("Razorpay did not return an order id");
        }
        return new GatewayOrder(
                PROVIDER,
                providerOrderId,
                creds.keyId(),          // publishable key — safe for the browser
                null,
                request.amount(),
                request.currency(),
                false);                 // the browser must complete the payment first
    }

    @Override
    public GatewayVerification verify(GatewayVerificationRequest request) {
        var creds = settingsService.razorpayCredentials();
        String orderId = request.providerOrderId();
        String paymentId = request.providerPaymentId();
        String signature = request.providerSignature();

        if (orderId == null || orderId.isBlank() || paymentId == null || paymentId.isBlank()
                || signature == null || signature.isBlank()) {
            return GatewayVerification.failure("Incomplete Razorpay payment details",
                    "{\"status\":\"invalid\",\"provider\":\"RAZORPAY\"}");
        }

        String expected = RazorpayClient.hmacSha256Hex(orderId + "|" + paymentId, creds.keySecret());
        if (!RazorpayClient.signaturesMatch(expected, signature)) {
            log.warn("Razorpay signature verification failed for order {}", orderId);
            return GatewayVerification.failure("Payment verification failed",
                    "{\"status\":\"signature_mismatch\",\"provider\":\"RAZORPAY\"}");
        }

        // Second, authoritative check straight from Razorpay.
        try {
            JsonNode payment = client.fetchPayment(creds.keyId(), creds.keySecret(), paymentId);
            String status = payment.path("status").asText("");
            String paidOrder = payment.path("order_id").asText("");
            if (!orderId.equals(paidOrder)) {
                return GatewayVerification.failure("Payment does not belong to this order",
                        "{\"status\":\"order_mismatch\",\"provider\":\"RAZORPAY\"}");
            }
            if (!"captured".equals(status) && !"authorized".equals(status)) {
                return GatewayVerification.failure("Payment was not completed (status: " + status + ")",
                        "{\"status\":\"" + status + "\",\"provider\":\"RAZORPAY\"}");
            }
            return GatewayVerification.success(paymentId, signature,
                    "{\"status\":\"" + status + "\",\"provider\":\"RAZORPAY\",\"payment_id\":\"" + paymentId + "\"}");
        } catch (BadRequestException e) {
            return GatewayVerification.failure(e.getMessage(),
                    "{\"status\":\"verification_error\",\"provider\":\"RAZORPAY\"}");
        }
    }

    @Override
    public GatewayRefund refund(String providerPaymentId, BigDecimal amount) {
        var creds = settingsService.razorpayCredentials();
        try {
            JsonNode refund = client.refund(creds.keyId(), creds.keySecret(), providerPaymentId, amount);
            return new GatewayRefund(true, refund.path("id").asText(null), amount, null);
        } catch (BadRequestException e) {
            return new GatewayRefund(false, null, amount, e.getMessage());
        }
    }
}

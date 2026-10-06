package com.vitc.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default in-house simulator. No network calls, no credentials — it mimics a
 * PSP well enough (order handle, payment id, signature, verification) for the
 * whole checkout flow to be exercised end to end. Selected at runtime by
 * {@link PaymentGatewayRouter} whenever Razorpay is not enabled.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "MOCK";

    @Override
    public String name() {
        return PROVIDER;
    }

    @Override
    public GatewayOrder createOrder(GatewayOrderRequest request) {
        String providerOrderId = "mock_order_" + shortId();
        return new GatewayOrder(
                PROVIDER,
                providerOrderId,
                "mock_key_vitc",
                null,
                request.amount(),
                request.currency(),
                true);
    }

    @Override
    public GatewayVerification verify(GatewayVerificationRequest request) {
        if (request.simulateFailure()) {
            return GatewayVerification.failure("Payment declined by the simulated gateway",
                    "{\"status\":\"failed\",\"provider\":\"MOCK\"}");
        }
        if (request.providerOrderId() == null || request.providerOrderId().isBlank()) {
            return GatewayVerification.failure("Missing provider order id", "{\"status\":\"invalid\"}");
        }
        String paymentId = request.providerPaymentId() == null || request.providerPaymentId().isBlank()
                ? "mock_pay_" + shortId()
                : request.providerPaymentId();
        String signature = "mocksig_" + Integer.toHexString((request.providerOrderId() + paymentId).hashCode());
        return GatewayVerification.success(paymentId, signature,
                "{\"status\":\"captured\",\"provider\":\"MOCK\",\"payment_id\":\"" + paymentId + "\"}");
    }

    @Override
    public GatewayRefund refund(String providerPaymentId, BigDecimal amount) {
        return new GatewayRefund(true, "mock_rfnd_" + shortId(), amount, null);
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}

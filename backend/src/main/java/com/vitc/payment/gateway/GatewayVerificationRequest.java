package com.vitc.payment.gateway;

public record GatewayVerificationRequest(
        String orderCode,
        String providerOrderId,
        String providerPaymentId,
        String providerSignature,
        boolean simulateFailure) {
}

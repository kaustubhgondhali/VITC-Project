package com.vitc.payment.gateway;

/** Normalised verification outcome shared by every provider implementation. */
public record GatewayVerification(
        boolean success,
        String providerPaymentId,
        String providerSignature,
        String rawResponse,
        String failureReason) {

    public static GatewayVerification success(String paymentId, String signature, String raw) {
        return new GatewayVerification(true, paymentId, signature, raw, null);
    }

    public static GatewayVerification failure(String reason, String raw) {
        return new GatewayVerification(false, null, null, raw, reason);
    }
}

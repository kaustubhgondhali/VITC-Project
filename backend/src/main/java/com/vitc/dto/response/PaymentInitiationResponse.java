package com.vitc.dto.response;

import java.math.BigDecimal;

/**
 * What the checkout page needs to hand control to a gateway. With the mock
 * gateway {@code autoConfirm} is true, so the browser immediately calls the
 * confirm endpoint. With Razorpay the client would open the checkout widget
 * using {@code providerOrderId} and {@code providerKey}.
 */
public record PaymentInitiationResponse(
        String provider,
        String orderCode,
        String transactionId,
        String providerOrderId,
        String providerKey,
        String checkoutUrl,
        BigDecimal amount,
        String currency,
        boolean autoConfirm,
        PaymentResponse payment) {
}

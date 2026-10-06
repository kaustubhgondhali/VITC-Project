package com.vitc.payment.gateway;

import java.math.BigDecimal;

/** Provider-side order handle returned when a payment attempt starts. */
public record GatewayOrder(
        String provider,
        String providerOrderId,
        String providerKey,
        String checkoutUrl,
        BigDecimal amount,
        String currency,
        boolean autoConfirm) {
}

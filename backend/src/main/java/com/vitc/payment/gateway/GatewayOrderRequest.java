package com.vitc.payment.gateway;

import java.math.BigDecimal;

public record GatewayOrderRequest(
        String orderCode,
        BigDecimal amount,
        String currency,
        String customerName,
        String email,
        String phone,
        String description) {
}

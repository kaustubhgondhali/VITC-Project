package com.vitc.payment.gateway;

import java.math.BigDecimal;

public record GatewayRefund(boolean success, String refundId, BigDecimal amount, String failureReason) {
}

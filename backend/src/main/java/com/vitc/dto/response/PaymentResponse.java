package com.vitc.dto.response;

import com.vitc.entity.enums.PaymentMethod;
import com.vitc.entity.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        String referenceType,
        Long referenceId,
        String orderCode,
        String transactionId,
        String payerName,
        String email,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String methodDetail,
        PaymentStatus status,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {
}

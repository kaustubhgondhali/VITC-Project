package com.vitc.mapper;

import com.vitc.dto.request.PaymentRequest;
import com.vitc.dto.response.PaymentResponse;
import com.vitc.entity.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static Payment toEntity(PaymentRequest request, String transactionId) {
        Payment entity = new Payment();
        entity.setReferenceType(request.referenceType().toUpperCase());
        entity.setReferenceId(request.referenceId());
        entity.setPayerName(request.payerName());
        entity.setEmail(request.email());
        entity.setAmount(request.amount());
        entity.setCurrency("INR");
        entity.setMethod(request.method());
        entity.setTransactionId(transactionId);
        entity.setProvider("MOCK");
        return entity;
    }

    public static PaymentResponse toResponse(Payment entity) {
        return new PaymentResponse(
                entity.getId(),
                entity.getReferenceType(),
                entity.getReferenceId(),
                entity.getOrder() != null ? entity.getOrder().getOrderCode() : null,
                entity.getTransactionId(),
                entity.getPayerName(),
                entity.getEmail(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getMethod(),
                entity.getMethodDetail(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getProviderOrderId(),
                entity.getProviderPaymentId(),
                entity.getFailureReason(),
                entity.getPaidAt(),
                entity.getCreatedAt());
    }
}

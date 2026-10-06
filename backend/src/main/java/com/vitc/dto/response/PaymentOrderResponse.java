package com.vitc.dto.response;

import com.vitc.entity.enums.PaymentOrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrderResponse(
        Long id,
        String orderCode,
        String itemType,
        Long itemRefId,
        String itemTitle,
        String itemMeta,
        String customerName,
        String email,
        String phone,
        String city,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        PaymentOrderStatus status,
        LocalDateTime createdAt) {
}

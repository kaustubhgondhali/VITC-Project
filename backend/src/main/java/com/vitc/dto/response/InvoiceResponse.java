package com.vitc.dto.response;

import com.vitc.entity.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String orderCode,
        String transactionId,
        String billingName,
        String billingEmail,
        String billingPhone,
        String billingAddress,
        String itemTitle,
        String itemMeta,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String gstNumber,
        InvoiceStatus status,
        LocalDateTime issuedAt) {
}

package com.vitc.mapper;

import com.vitc.dto.response.InvoiceResponse;
import com.vitc.entity.Invoice;

public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static InvoiceResponse toResponse(Invoice i) {
        return new InvoiceResponse(
                i.getId(),
                i.getInvoiceNumber(),
                i.getOrder() != null ? i.getOrder().getOrderCode() : null,
                i.getPayment() != null ? i.getPayment().getTransactionId() : null,
                i.getBillingName(),
                i.getBillingEmail(),
                i.getBillingPhone(),
                i.getBillingAddress(),
                i.getOrder() != null ? i.getOrder().getItemTitle() : null,
                i.getOrder() != null ? i.getOrder().getItemMeta() : null,
                i.getSubtotal(),
                i.getDiscountAmount(),
                i.getTaxAmount(),
                i.getTotalAmount(),
                i.getGstNumber(),
                i.getStatus(),
                i.getIssuedAt());
    }
}

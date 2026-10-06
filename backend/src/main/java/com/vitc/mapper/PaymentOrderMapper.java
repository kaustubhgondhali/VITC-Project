package com.vitc.mapper;

import com.vitc.dto.response.PaymentOrderResponse;
import com.vitc.entity.PaymentOrder;

public final class PaymentOrderMapper {

    private PaymentOrderMapper() {
    }

    public static PaymentOrderResponse toResponse(PaymentOrder o) {
        return new PaymentOrderResponse(
                o.getId(),
                o.getOrderCode(),
                o.getItemType(),
                o.getItemRefId(),
                o.getItemTitle(),
                o.getItemMeta(),
                o.getCustomerName(),
                o.getEmail(),
                o.getPhone(),
                o.getCity(),
                o.getCouponCode(),
                o.getSubtotal(),
                o.getDiscountAmount(),
                o.getTaxRate(),
                o.getTaxAmount(),
                o.getTotalAmount(),
                o.getCurrency(),
                o.getStatus(),
                o.getCreatedAt());
    }
}

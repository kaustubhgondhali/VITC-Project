package com.vitc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderResponse(
        Long id,
        String orderCode,
        String orderType,
        String customerName,
        String email,
        String phone,
        String product,
        BigDecimal amount,
        String paymentStatus,
        String orderStatus,
        LocalDateTime createdAt,
        /** True when the Main Admin can upload/deliver a project package for this row. */
        Boolean canDeliver,
        /** Set once an assignment package has been delivered; null otherwise. */
        AssignmentDeliveryInfo delivery) {
}

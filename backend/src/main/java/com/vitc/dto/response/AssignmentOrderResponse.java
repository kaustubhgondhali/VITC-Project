package com.vitc.dto.response;

import com.vitc.entity.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssignmentOrderResponse(
        Long id,
        String orderCode,
        Long assignmentId,
        String assignmentTitle,
        String studentName,
        String email,
        String phone,
        String college,
        BigDecimal amount,
        OrderStatus status,
        String requirements,
        LocalDateTime createdAt) {
}

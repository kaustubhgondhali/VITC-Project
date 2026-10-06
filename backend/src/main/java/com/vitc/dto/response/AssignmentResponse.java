package com.vitc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssignmentResponse(
        Long id,
        String code,
        String title,
        String description,
        BigDecimal price,
        String tech,
        String difficulty,
        String deliveryDays,
        String category,
        String icon,
        String features,
        Boolean active,
        LocalDateTime createdAt) {
}

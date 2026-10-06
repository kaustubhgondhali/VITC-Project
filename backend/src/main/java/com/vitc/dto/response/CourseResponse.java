package com.vitc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String code,
        String title,
        String description,
        BigDecimal price,
        String meta,
        String level,
        String category,
        String durationMonths,
        String icon,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}

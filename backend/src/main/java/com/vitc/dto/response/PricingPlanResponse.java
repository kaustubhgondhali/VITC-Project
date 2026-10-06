package com.vitc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PricingPlanResponse(
    Long id,
    String name,
    String tagline,
    BigDecimal price,
    BigDecimal oldPrice,
    String currency,
    String billingPeriod,
    String features,
    List<String> featureList,
    String category,
    Integer displayOrder,
    Boolean highlighted,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}

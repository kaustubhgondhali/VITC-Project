package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "PricingPlanRequest", description = "Payload to create or update a pricing plan")
public record PricingPlanRequest(
    @NotBlank(message = "Name is required") @Size(max = 120) String name,
    @Size(max = 300) String tagline,
    @NotNull(message = "Price is required") @DecimalMin(value = "0.0", message = "Price must be positive") BigDecimal price,
    @DecimalMin(value = "0.0", message = "Old price must be positive") BigDecimal oldPrice,
    @Size(max = 40) String currency,
    @Size(max = 40) String billingPeriod,
    @Size(max = 4000) String features,
    @Size(max = 60) String category,
    @Min(value = 0, message = "Display order cannot be negative") Integer displayOrder,
    Boolean highlighted,
    Boolean active) {
}

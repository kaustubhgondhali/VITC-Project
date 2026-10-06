package com.vitc.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AssignmentRequest(
    @NotBlank(message = "Assignment code is required") @Size(max = 60) String code,
    @NotBlank(message = "Title is required") @Size(max = 180) String title,
    @Size(max = 2000) String description,
    @NotNull(message = "Price is required") @DecimalMin(value = "0.0") BigDecimal price,
    @Size(max = 120) String tech,
    @Size(max = 40) String difficulty,
    @Size(max = 40) String deliveryDays,
    @Size(max = 60) String category,
    @Size(max = 20) String icon,
    @Size(max = 2000) String features,
    Boolean active) {
}

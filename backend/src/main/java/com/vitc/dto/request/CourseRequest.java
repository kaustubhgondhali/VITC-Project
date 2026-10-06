package com.vitc.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CourseRequest(
    @NotBlank(message = "Course code is required") @Size(max = 60) String code,
    @NotBlank(message = "Title is required") @Size(max = 150) String title,
    @Size(max = 2000) String description,
    @NotNull(message = "Price is required") @DecimalMin(value = "0.0", message = "Price must be positive") BigDecimal price,
    @Size(max = 120) String meta,
    @Size(max = 60) String level,
    @Size(max = 60) String category,
    @Size(max = 40) String durationMonths,
    @Size(max = 20) String icon,
    Boolean active) {
}

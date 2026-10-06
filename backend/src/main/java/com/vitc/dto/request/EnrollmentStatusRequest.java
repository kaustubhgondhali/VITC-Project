package com.vitc.dto.request;

import com.vitc.entity.enums.EnrollmentStatus;
import jakarta.validation.constraints.NotNull;

public record EnrollmentStatusRequest(@NotNull(message = "Status is required") EnrollmentStatus status) {
}

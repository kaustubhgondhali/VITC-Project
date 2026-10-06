package com.vitc.dto.request;

import com.vitc.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentStatusRequest(@NotNull(message = "Status is required") PaymentStatus status) {
}

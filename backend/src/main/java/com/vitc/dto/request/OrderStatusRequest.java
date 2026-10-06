package com.vitc.dto.request;

import com.vitc.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusRequest(@NotNull(message = "Status is required") OrderStatus status) {
}

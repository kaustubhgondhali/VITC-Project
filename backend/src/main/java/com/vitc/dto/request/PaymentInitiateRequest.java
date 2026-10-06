package com.vitc.dto.request;

import com.vitc.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentInitiateRequest(
        @NotNull(message = "Payment method is required") PaymentMethod method,
        /** UPI id, masked card number, wallet name or bank name. */
        @Size(max = 120) String methodDetail) {
}

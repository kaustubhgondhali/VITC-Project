package com.vitc.dto.request;

import com.vitc.entity.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank(message = "Reference type is required")
        @Pattern(regexp = "COURSE|ASSIGNMENT", message = "Reference type must be COURSE or ASSIGNMENT")
        String referenceType,
        @NotNull(message = "Reference id is required") Long referenceId,
        @NotBlank(message = "Payer name is required") @Size(max = 120) String payerName,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @NotNull(message = "Amount is required") @DecimalMin(value = "1.0", message = "Amount must be at least 1") BigDecimal amount,
        @NotNull(message = "Payment method is required") PaymentMethod method) {
}

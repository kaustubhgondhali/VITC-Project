package com.vitc.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CheckoutOrderRequest(
        @NotBlank(message = "Item type is required")
        @Pattern(regexp = "COURSE|ASSIGNMENT", message = "Item type must be COURSE or ASSIGNMENT")
        String itemType,
        Long itemRefId,
        @NotBlank(message = "Item title is required") @Size(max = 200) String itemTitle,
        @Size(max = 250) String itemMeta,
        @NotBlank(message = "Customer name is required") @Size(max = 120) String customerName,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @Size(max = 20) String phone,
        @Size(max = 120) String city,
        @Size(max = 40) String couponCode,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.0", message = "Amount must be at least 1")
        BigDecimal subtotal) {
}

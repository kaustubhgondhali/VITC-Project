package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gateway callback payload. The mock gateway fills these itself; Razorpay will
 * send razorpay_order_id / razorpay_payment_id / razorpay_signature into the
 * very same fields.
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "Provider order id is required") @Size(max = 120) String providerOrderId,
        @NotBlank(message = "Provider payment id is required") @Size(max = 120) String providerPaymentId,
        @Size(max = 255) String providerSignature,
        Boolean simulateFailure) {
}

package com.vitc.dto.response;

/** Everything the confirmation screen needs in one round trip. */
public record OrderConfirmationResponse(
        PaymentOrderResponse order,
        PaymentResponse payment,
        InvoiceResponse invoice,
        /**
         * One-time student account info (login id + whether it's new).
         * Present only on the response to the payment-verification call that
         * created the account; every later read of the same order returns
         * null here. Never carries the temporary password - see
         * {@link StudentCredentialsResponse}.
         */
        StudentCredentialsResponse studentAccount) {

    public OrderConfirmationResponse(PaymentOrderResponse order, PaymentResponse payment, InvoiceResponse invoice) {
        this(order, payment, invoice, null);
    }
}

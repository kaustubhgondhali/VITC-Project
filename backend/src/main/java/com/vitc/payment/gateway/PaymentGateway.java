package com.vitc.payment.gateway;

import java.math.BigDecimal;

/**
 * Provider-agnostic payment port.
 *
 * <p>Only this interface is referenced by the checkout service, so swapping the
 * mock implementation for Razorpay (or any other PSP) is a configuration change
 * ({@code payment.gateway=razorpay}) plus one new implementation class — no
 * service, controller, entity or frontend change required.</p>
 */
public interface PaymentGateway {

    /** Provider identifier persisted on every payment row, e.g. MOCK / RAZORPAY. */
    String name();

    /** Creates the provider-side order that the client checkout will settle. */
    GatewayOrder createOrder(GatewayOrderRequest request);

    /** Verifies the provider callback / signature and reports the outcome. */
    GatewayVerification verify(GatewayVerificationRequest request);

    /** Refunds a captured payment. */
    GatewayRefund refund(String providerPaymentId, BigDecimal amount);
}

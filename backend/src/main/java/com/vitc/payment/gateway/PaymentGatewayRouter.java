package com.vitc.payment.gateway;

import com.vitc.entity.enums.PaymentGatewayType;
import com.vitc.service.PaymentSettingsService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The single {@link PaymentGateway} the checkout service sees. It delegates to
 * the mock simulator or the real Razorpay adapter based on the configuration
 * saved in Admin -> Payment Settings, so the existing checkout flow needed no
 * change at all.
 */
@Primary
@Component
@RequiredArgsConstructor
public class PaymentGatewayRouter implements PaymentGateway {

    private final PaymentSettingsService settingsService;
    private final MockPaymentGateway mockGateway;
    private final RazorpayPaymentGateway razorpayGateway;

    private PaymentGateway delegate() {
        return settingsService.activeGateway() == PaymentGatewayType.RAZORPAY ? razorpayGateway : mockGateway;
    }

    @Override
    public String name() {
        return delegate().name();
    }

    @Override
    public GatewayOrder createOrder(GatewayOrderRequest request) {
        return delegate().createOrder(request);
    }

    @Override
    public GatewayVerification verify(GatewayVerificationRequest request) {
        return delegate().verify(request);
    }

    @Override
    public GatewayRefund refund(String providerPaymentId, BigDecimal amount) {
        return delegate().refund(providerPaymentId, amount);
    }
}

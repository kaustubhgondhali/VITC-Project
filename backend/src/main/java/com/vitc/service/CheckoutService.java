package com.vitc.service;

import com.vitc.dto.request.CheckoutOrderRequest;
import com.vitc.dto.request.PaymentConfirmRequest;
import com.vitc.dto.request.PaymentInitiateRequest;
import com.vitc.dto.response.OrderConfirmationResponse;
import com.vitc.dto.response.PaymentInitiationResponse;
import com.vitc.dto.response.PaymentOrderResponse;
import java.util.List;

public interface CheckoutService {

    List<PaymentOrderResponse> getAllOrders();

    PaymentOrderResponse createOrder(CheckoutOrderRequest request);

    PaymentOrderResponse getOrder(String orderCode);

    List<PaymentOrderResponse> getOrdersByEmail(String email);

    PaymentInitiationResponse initiatePayment(String orderCode, PaymentInitiateRequest request);

    OrderConfirmationResponse confirmPayment(String orderCode, PaymentConfirmRequest request);

    OrderConfirmationResponse getConfirmation(String orderCode);

    PaymentOrderResponse cancelOrder(String orderCode);

    /**
     * Settles a payment reported by an already signature-verified Razorpay
     * webhook. Idempotent: a replayed webhook is a no-op.
     */
    void settleVerifiedWebhookPayment(String providerOrderId, String providerPaymentId);
}

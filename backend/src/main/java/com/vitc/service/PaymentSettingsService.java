package com.vitc.service;

import com.vitc.dto.request.PaymentSettingsRequest;
import com.vitc.dto.response.ConnectionTestResponse;
import com.vitc.dto.response.GatewayStatusResponse;
import com.vitc.dto.response.PaymentSettingsResponse;
import com.vitc.entity.enums.PaymentGatewayType;

public interface PaymentSettingsService {

    /** Admin view — never contains the key secret. */
    PaymentSettingsResponse getSettings();

    /** Public view used by the storefront to decide which checkout to open. */
    GatewayStatusResponse getPublicStatus();

    PaymentSettingsResponse save(PaymentSettingsRequest request);

    PaymentSettingsResponse setEnabled(boolean enabled);

    ConnectionTestResponse testConnection();

    /** Gateway actually in force right now (falls back to MOCK). */
    PaymentGatewayType activeGateway();

    /** Server-side only: decrypted Razorpay credentials. */
    RazorpayCredentials razorpayCredentials();

    record RazorpayCredentials(String keyId, String keySecret, String webhookSecret, String mode) {
    }
}

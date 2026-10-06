package com.vitc.service.impl;

import com.vitc.dto.request.PaymentSettingsRequest;
import com.vitc.dto.response.ConnectionTestResponse;
import com.vitc.dto.response.GatewayStatusResponse;
import com.vitc.dto.response.PaymentSettingsResponse;
import com.vitc.entity.PaymentGatewaySetting;
import com.vitc.entity.enums.PaymentGatewayMode;
import com.vitc.entity.enums.PaymentGatewayType;
import com.vitc.exception.BadRequestException;
import com.vitc.payment.gateway.RazorpayClient;
import com.vitc.repository.PaymentGatewaySettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.service.PaymentSettingsService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single {@code payment_gateway_settings} row. Secrets are encrypted
 * on the way in and only ever decrypted server side.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentSettingsServiceImpl implements PaymentSettingsService {

    private static final String KEY = "DEFAULT";
    private static final String MASK = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private final PaymentGatewaySettingRepository repository;
    private final CryptoService crypto;
    private final RazorpayClient razorpayClient;

    /** Legacy property default: used only until an admin saves settings. */
    @Value("${payment.gateway:mock}")
    private String propertyGateway;

    @Override
    public PaymentSettingsResponse getSettings() {
        return toResponse(current());
    }

    @Override
    public GatewayStatusResponse getPublicStatus() {
        PaymentGatewaySetting s = current();
        PaymentGatewayType active = resolve(s);
        return new GatewayStatusResponse(
                active.name(),
                active == PaymentGatewayType.RAZORPAY ? s.getMode().name() : "TEST",
                active == PaymentGatewayType.RAZORPAY && s.getMode() == PaymentGatewayMode.LIVE);
    }

    @Override
    @Transactional
    public PaymentSettingsResponse save(PaymentSettingsRequest request) {
        PaymentGatewaySetting s = current();

        PaymentGatewayType gateway = parseGateway(request.gateway());
        PaymentGatewayMode mode = parseMode(request.mode());

        String keyId = trimToNull(request.keyId());
        String keySecret = trimToNull(request.keySecret());

        if (gateway == PaymentGatewayType.RAZORPAY) {
            if (keyId == null) {
                throw new BadRequestException("Razorpay Key ID is required");
            }
            if (keySecret == null && s.getEncryptedKeySecret() == null) {
                throw new BadRequestException("Razorpay Key Secret is required");
            }
            if (!keyId.startsWith("rzp_")) {
                throw new BadRequestException("Razorpay Key ID should start with rzp_test_ or rzp_live_");
            }
            if (mode == PaymentGatewayMode.LIVE && keyId.startsWith("rzp_test_")) {
                throw new BadRequestException("A test Key ID cannot be used in LIVE mode");
            }
            if (mode == PaymentGatewayMode.TEST && keyId.startsWith("rzp_live_")) {
                throw new BadRequestException("A live Key ID cannot be used in TEST mode");
            }
        }

        s.setGateway(gateway);
        s.setMode(mode);
        s.setKeyId(keyId);
        if (keySecret != null) {
            s.setEncryptedKeySecret(crypto.encrypt(keySecret));
            s.setLastTestStatus(null);
            s.setLastTestMessage(null);
        }
        String webhookSecret = trimToNull(request.webhookSecret());
        if (webhookSecret != null) {
            s.setEncryptedWebhookSecret(crypto.encrypt(webhookSecret));
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (gateway == PaymentGatewayType.MOCK) {
            enabled = false;
        }
        s.setEnabled(enabled);
        return toResponse(repository.save(s));
    }

    @Override
    @Transactional
    public PaymentSettingsResponse setEnabled(boolean enabled) {
        PaymentGatewaySetting s = current();
        if (enabled) {
            if (s.getGateway() != PaymentGatewayType.RAZORPAY) {
                throw new BadRequestException("Select Razorpay as the gateway before enabling it");
            }
            if (s.getKeyId() == null || s.getEncryptedKeySecret() == null) {
                throw new BadRequestException("Razorpay is not configured yet — save the Key ID and Key Secret first");
            }
        }
        s.setEnabled(enabled);
        return toResponse(repository.save(s));
    }

    @Override
    @Transactional
    public ConnectionTestResponse testConnection() {
        PaymentGatewaySetting s = current();
        if (s.getGateway() != PaymentGatewayType.RAZORPAY) {
            return new ConnectionTestResponse(true, "Mock gateway selected — no external connection required");
        }
        if (s.getKeyId() == null || s.getEncryptedKeySecret() == null) {
            return new ConnectionTestResponse(false, "Razorpay is not configured yet");
        }
        ConnectionTestResponse result;
        try {
            razorpayClient.ping(s.getKeyId(), crypto.decrypt(s.getEncryptedKeySecret()));
            result = new ConnectionTestResponse(true, "Razorpay connection successful");
        } catch (BadRequestException e) {
            result = new ConnectionTestResponse(false, e.getMessage());
        } catch (Exception e) {
            log.warn("Razorpay connection test failed: {}", e.toString());
            result = new ConnectionTestResponse(false, "Could not reach Razorpay. Please try again later.");
        }
        s.setLastTestStatus(result.success() ? "SUCCESS" : "FAILED");
        s.setLastTestMessage(result.message());
        repository.save(s);
        return result;
    }

    @Override
    public PaymentGatewayType activeGateway() {
        return resolve(current());
    }

    @Override
    public RazorpayCredentials razorpayCredentials() {
        PaymentGatewaySetting s = current();
        if (s.getKeyId() == null || s.getEncryptedKeySecret() == null) {
            throw new BadRequestException("Razorpay is not configured. Configure it in Admin -> Payment Settings.");
        }
        return new RazorpayCredentials(
                s.getKeyId(),
                crypto.decrypt(s.getEncryptedKeySecret()),
                s.getEncryptedWebhookSecret() == null ? null : crypto.decrypt(s.getEncryptedWebhookSecret()),
                s.getMode().name());
    }

    /* ------------------------------------------------------------------ */

    private PaymentGatewaySetting current() {
        return repository.findBySettingsKey(KEY).orElseGet(() -> PaymentGatewaySetting.builder()
                .settingsKey(KEY)
                .gateway("razorpay".equalsIgnoreCase(propertyGateway)
                        ? PaymentGatewayType.RAZORPAY : PaymentGatewayType.MOCK)
                .mode(PaymentGatewayMode.TEST)
                .enabled(false)
                .build());
    }

    /** Razorpay only takes over when it is selected, configured AND enabled. */
    private PaymentGatewayType resolve(PaymentGatewaySetting s) {
        boolean razorpayReady = s.getGateway() == PaymentGatewayType.RAZORPAY
                && Boolean.TRUE.equals(s.getEnabled())
                && s.getKeyId() != null
                && s.getEncryptedKeySecret() != null;
        return razorpayReady ? PaymentGatewayType.RAZORPAY : PaymentGatewayType.MOCK;
    }

    private PaymentSettingsResponse toResponse(PaymentGatewaySetting s) {
        boolean configured = s.getKeyId() != null && s.getEncryptedKeySecret() != null;
        String status;
        if (s.getGateway() == PaymentGatewayType.MOCK) {
            status = "MOCK_GATEWAY";
        } else if (!configured) {
            status = "NOT_CONFIGURED";
        } else if ("FAILED".equals(s.getLastTestStatus())) {
            status = "CONNECTION_FAILED";
        } else if (Boolean.TRUE.equals(s.getEnabled())) {
            status = "ENABLED";
        } else {
            status = "SUCCESS".equals(s.getLastTestStatus()) ? "CONNECTED" : "DISABLED";
        }
        return new PaymentSettingsResponse(
                s.getGateway().name(),
                s.getKeyId(),
                configured ? MASK : null,
                s.getEncryptedWebhookSecret() != null,
                s.getMode().name(),
                Boolean.TRUE.equals(s.getEnabled()),
                configured,
                status,
                s.getLastTestStatus(),
                s.getLastTestMessage(),
                s.getUpdatedAt());
    }

    private static PaymentGatewayType parseGateway(String value) {
        try {
            return PaymentGatewayType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BadRequestException("Unsupported gateway: " + value + " (use MOCK or RAZORPAY)");
        }
    }

    private static PaymentGatewayMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return PaymentGatewayMode.TEST;
        }
        try {
            return PaymentGatewayMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BadRequestException("Invalid mode: " + value + " (use TEST or LIVE)");
        }
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

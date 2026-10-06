package com.vitc.entity;

import com.vitc.entity.enums.PaymentGatewayMode;
import com.vitc.entity.enums.PaymentGatewayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row configuration of the payment gateway used by this installation.
 *
 * <p>The Razorpay key secret is stored AES-GCM encrypted (see
 * {@code com.vitc.security.CryptoService}); the plain text never leaves the
 * server and is never returned by any API.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_gateway_settings")
public class PaymentGatewaySetting extends BaseEntity {

    /** Guarantees a single configuration row per installation. */
    @Column(name = "settings_key", nullable = false, unique = true, length = 40)
    @Builder.Default
    private String settingsKey = "DEFAULT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentGatewayType gateway = PaymentGatewayType.MOCK;

    @Column(name = "key_id", length = 120)
    private String keyId;

    /** AES-GCM ciphertext. Never exposed through the API. */
    @Column(name = "encrypted_key_secret", length = 512)
    private String encryptedKeySecret;

    /** AES-GCM ciphertext of the webhook secret (optional). */
    @Column(name = "encrypted_webhook_secret", length = 512)
    private String encryptedWebhookSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private PaymentGatewayMode mode = PaymentGatewayMode.TEST;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /** Outcome of the last "Test connection" run, for display only. */
    @Column(name = "last_test_status", length = 40)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 300)
    private String lastTestMessage;
}

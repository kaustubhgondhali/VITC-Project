package com.vitc.entity;

import com.vitc.entity.enums.SmtpSecurityMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row SMTP / outgoing-mail configuration for this installation,
 * managed from Main Admin -> Email / SMTP Settings.
 *
 * <p>The password (or app password) is stored AES-GCM encrypted (see
 * {@code com.vitc.security.CryptoService}); the plain text never leaves the
 * server and is never returned by any API response.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "smtp_settings")
public class SmtpSetting extends BaseEntity {

    /** Guarantees a single configuration row per installation. */
    @Column(name = "settings_key", nullable = false, unique = true, length = 40)
    @Builder.Default
    private String settingsKey = "DEFAULT";

    @Column(name = "host", length = 200)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "username", length = 200)
    private String username;

    /** AES-GCM ciphertext. Never exposed through the API. */
    @Column(name = "encrypted_password", length = 512)
    private String encryptedPassword;

    @Column(name = "from_email", length = 200)
    private String fromEmail;

    @Column(name = "from_name", length = 150)
    private String fromName;

    /* ---- Email Configuration (provider-aware) - all nullable, so rows saved before these
       columns existed keep working exactly as before (see database/smtp_email_configuration_upgrade.sql). */

    /** Provider code from {@code MailProviderRegistry}, e.g. GMAIL or CUSTOM. */
    @Column(name = "provider", length = 40)
    private String provider;

    /** Null on legacy rows - the transport is then derived from the port, as before. */
    @Enumerated(EnumType.STRING)
    @Column(name = "security_mode", length = 20)
    private SmtpSecurityMode securityMode;

    @Column(name = "reply_to_email", length = 200)
    private String replyToEmail;

    /** Informational only - VITC does not verify DNS/SPF/DKIM itself. */
    @Column(name = "sending_domain", length = 190)
    private String sendingDomain;

    /** Informational only - VITC does not sign messages with DKIM itself. */
    @Column(name = "dkim_selector", length = 100)
    private String dkimSelector;

    /** Null on legacy rows, which is treated as enabled. */
    @Column(name = "enabled")
    private Boolean enabled;

    /** Username of the Main Admin who last saved the configuration. */
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    /* ---- "Connect Google Account" (Gmail API over OAuth 2.0) - no App Password involved. ---- */

    /** SMTP (default / null) or GMAIL_OAUTH when a Google account is connected. */
    @Column(name = "auth_mode", length = 20)
    private String authMode;

    /** The connected Google address (also used as the From address - Gmail requires that). */
    @Column(name = "google_email", length = 200)
    private String googleEmail;

    /** AES-GCM ciphertext of the Google refresh token. Never exposed through the API. */
    @Column(name = "google_refresh_token_enc", length = 1024)
    private String googleRefreshTokenEnc;

    @Column(name = "google_scope", length = 500)
    private String googleScope;

    @Column(name = "google_connected_at")
    private LocalDateTime googleConnectedAt;

    @Column(name = "google_connected_by", length = 120)
    private String googleConnectedBy;

    public static final String AUTH_GMAIL_OAUTH = "GMAIL_OAUTH";

    public boolean googleConnected() {
        return AUTH_GMAIL_OAUTH.equals(authMode) && googleRefreshTokenEnc != null && !googleRefreshTokenEnc.isBlank();
    }

    public boolean isSendingEnabled() {
        return enabled == null || enabled;
    }

    public SmtpSecurityMode effectiveSecurityMode() {
        return securityMode != null ? securityMode : SmtpSecurityMode.derivedFromPort(port);
    }
}

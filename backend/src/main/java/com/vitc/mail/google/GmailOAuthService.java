package com.vitc.mail.google;

import com.vitc.dto.response.GoogleConnectionResponse;
import com.vitc.entity.Admin;
import com.vitc.entity.SmtpSetting;
import com.vitc.mail.MailProviderRegistry;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns VITC's Google connection: the authorisation round trip, the encrypted refresh token, and
 * the supply of short-lived access tokens to {@link GmailApiMailSender}.
 *
 * <p>Token handling rules enforced here:</p>
 * <ul>
 *   <li>the refresh token is encrypted at rest with the same {@code CryptoService} (AES-256-GCM)
 *       as the SMTP password and the payment gateway secrets;</li>
 *   <li>access tokens are held in memory only, never written to the database;</li>
 *   <li>no token, code or client secret is ever logged or returned from a method that feeds an
 *       API response.</li>
 * </ul>
 *
 * <p>The SMTP columns of the row are never touched here, so an existing App Password
 * configuration survives as a fallback and is used again after Disconnect.</p>
 */
@Slf4j
@Service
public class GmailOAuthService {

    private static final String KEY = "DEFAULT";
    private static final String ENTITY = "SmtpSetting";

    private final SmtpSettingRepository repository;
    private final CryptoService crypto;
    private final GoogleOAuthClient client;
    private final GoogleOAuthStateService stateService;
    private final GoogleOAuthProperties properties;
    private final AuditLogService auditLogService;
    private final String defaultSenderName;

    /** Current access token. In memory only, and dropped whenever the connection changes. */
    private volatile GoogleTokens cachedAccess;
    private volatile GmailApiMailSender cachedSender;

    public GmailOAuthService(SmtpSettingRepository repository, CryptoService crypto, GoogleOAuthClient client,
                             GoogleOAuthStateService stateService, GoogleOAuthProperties properties,
                             AuditLogService auditLogService,
                             @Value("${app.mail.from-name:VITC}") String defaultSenderName) {
        this.repository = repository;
        this.crypto = crypto;
        this.client = client;
        this.stateService = stateService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        String name = defaultSenderName == null ? "" : defaultSenderName.replaceAll("[\\r\\n<>]", "").trim();
        this.defaultSenderName = name.isEmpty() ? "VITC" : name;
    }

    public boolean configured() {
        return properties.isConfigured();
    }

    /** Safe connection information for the admin screen - never a token or the client secret. */
    public GoogleConnectionResponse status() {
        SmtpSetting s = repository.findBySettingsKey(KEY).orElse(null);
        boolean connected = s != null && s.googleConnected();
        List<String> missing = new ArrayList<>();
        if (properties.clientId().isEmpty()) {
            missing.add("GOOGLE_CLIENT_ID");
        }
        if (properties.clientSecret().isEmpty()) {
            missing.add("GOOGLE_CLIENT_SECRET");
        }
        if (properties.redirectUri().isEmpty()) {
            missing.add("GOOGLE_REDIRECT_URI");
        }
        return new GoogleConnectionResponse(
                configured(),
                connected,
                connected ? s.getGoogleEmail() : null,
                connected ? s.getGoogleConnectedAt() : null,
                connected ? s.getGoogleConnectedBy() : null,
                properties.redirectUri().isEmpty() ? null : properties.redirectUri(),
                missing);
    }

    // ---- connect ------------------------------------------------------------------------------

    /** Step 1: where to send the admin's browser. Carries a signed, single-use state value. */
    public String authorizationUrl(Long adminId, String loginHint) {
        if (!configured()) {
            throw new GoogleAuthException("Google sign-in is not configured on this server. Set GOOGLE_CLIENT_ID, "
                    + "GOOGLE_CLIENT_SECRET and GOOGLE_REDIRECT_URI (for example in backend/.env), then restart "
                    + "the backend.", false);
        }
        return client.authorizationUrl(stateService.issue(adminId), loginHint);
    }

    /** Validates the state Google echoed back, returning the admin who started the flow. */
    public Optional<Long> consumeState(String state) {
        return stateService.consume(state);
    }

    /**
     * Step 2: turn the authorization code into a stored connection.
     *
     * @return the connected Google address
     */
    @Transactional
    public String completeConnection(String code, Admin actor) {
        GoogleTokens tokens = client.exchangeCode(code);
        if (!tokens.hasRefreshToken()) {
            // Without a refresh token the connection would die within the hour.
            throw new GoogleAuthException("Google did not return a long-lived authorization. Remove VITC from your "
                    + "Google account's third-party access list and connect again.", false);
        }
        if (!tokens.grantsSend()) {
            throw new GoogleAuthException("Permission to send email was not granted. Please connect again and leave "
                    + "the \"Send email on your behalf\" permission ticked.", false);
        }

        SmtpSetting s = repository.findBySettingsKey(KEY).orElseGet(() -> {
            SmtpSetting fresh = SmtpSetting.builder().settingsKey(KEY).build();
            fresh.setProvider(MailProviderRegistry.GMAIL);
            fresh.setFromName(defaultSenderName);
            // A connection made from scratch is meant to be used.
            fresh.setEnabled(Boolean.TRUE);
            return fresh;
        });
        boolean created = s.getId() == null;

        String email = tokens.email();
        if (email == null && s.getGoogleEmail() != null) {
            email = s.getGoogleEmail();
        }

        s.setAuthMode(SmtpSetting.AUTH_GMAIL_OAUTH);
        s.setGoogleEmail(email);
        s.setGoogleRefreshTokenEnc(crypto.encrypt(tokens.refreshToken()));
        s.setGoogleScope(tokens.scope());
        s.setGoogleConnectedAt(LocalDateTime.now());
        s.setGoogleConnectedBy(actor.getUsername());
        s.setUpdatedBy(actor.getUsername());
        if (s.getFromName() == null || s.getFromName().isBlank()) {
            s.setFromName(defaultSenderName);
        }
        if (s.getEnabled() == null) {
            s.setEnabled(Boolean.TRUE);
        }
        s = repository.save(s);
        invalidate();
        // Hold the access token we already have rather than immediately asking for another.
        cachedAccess = tokens;

        auditLogService.log(created ? "SMTP_CONFIGURATION_CREATED" : "SMTP_CONFIGURATION_UPDATED", ENTITY,
                String.valueOf(s.getId()), "Google account connected for " + email + " (Gmail API, send only)");
        log.info("Gmail OAuth connected for {} (authorised by {})", email, actor.getUsername());
        return email;
    }

    // ---- disconnect ---------------------------------------------------------------------------

    /**
     * Withdraws the connection: asks Google to revoke the grant, then deletes the stored token and
     * returns the row to SMTP mode. The SMTP settings are untouched, so sending falls back to them
     * if they are configured.
     */
    @Transactional
    public String disconnect(String actorUsername) {
        SmtpSetting s = repository.findBySettingsKey(KEY)
                .filter(SmtpSetting::googleConnected)
                .orElseThrow(() -> new GoogleAuthException("No Google account is connected.", false));

        String email = s.getGoogleEmail();
        boolean revoked = false;
        try {
            revoked = client.revoke(crypto.decrypt(s.getGoogleRefreshTokenEnc()));
        } catch (RuntimeException e) {
            // The stored token no longer decrypts; nothing to revoke, but it must still be cleared.
            log.warn("Stored Google authorization could not be read for revocation; clearing it anyway.");
        }

        s.setAuthMode(null);
        s.setGoogleRefreshTokenEnc(null);
        s.setGoogleEmail(null);
        s.setGoogleScope(null);
        s.setGoogleConnectedAt(null);
        s.setGoogleConnectedBy(null);
        s.setUpdatedBy(actorUsername);
        repository.save(s);
        invalidate();

        auditLogService.log("SMTP_CONFIGURATION_UPDATED", ENTITY, String.valueOf(s.getId()),
                "Google account disconnected" + (email == null ? "" : " for " + email)
                        + (revoked ? " (access revoked at Google)" : " (local authorization removed)"));
        log.info("Gmail OAuth disconnected for {} by {}", email, actorUsername);
        return revoked
                ? "Google account disconnected and access revoked at Google."
                : "Google account disconnected. The stored authorization has been removed.";
    }

    // ---- sending ------------------------------------------------------------------------------

    /** The mail sender for the connected account, or empty when no Google account is connected. */
    public Optional<GmailApiMailSender> senderFor(SmtpSetting settings) {
        if (settings == null || !settings.googleConnected() || !configured()) {
            return Optional.empty();
        }
        GmailApiMailSender sender = cachedSender;
        if (sender == null) {
            sender = new GmailApiMailSender(client, this::accessToken);
            cachedSender = sender;
        }
        return Optional.of(sender);
    }

    /**
     * A valid access token, refreshed from the stored refresh token when the cached one has aged
     * out. Called on every send, so the common path is a cache hit with no network round trip.
     */
    public String accessToken() {
        GoogleTokens current = cachedAccess;
        if (current != null && current.accessToken() != null && !current.expired()) {
            return current.accessToken();
        }
        synchronized (this) {
            current = cachedAccess;
            if (current != null && current.accessToken() != null && !current.expired()) {
                return current.accessToken();
            }
            SmtpSetting s = repository.findBySettingsKey(KEY)
                    .filter(SmtpSetting::googleConnected)
                    .orElseThrow(() -> new GoogleAuthException(
                            "No Google account is connected. Connect one in Admin -> Email / SMTP Settings.", false));
            String refreshToken;
            try {
                refreshToken = crypto.decrypt(s.getGoogleRefreshTokenEnc());
            } catch (RuntimeException e) {
                throw new GoogleAuthException("The stored Google authorization can no longer be decrypted "
                        + "(the server encryption key changed). Please reconnect your Google account.", true, e);
            }
            GoogleTokens refreshed = client.refreshAccessToken(refreshToken);
            if (refreshed.accessToken() == null) {
                throw GoogleAuthException.revoked();
            }
            cachedAccess = refreshed;
            return refreshed.accessToken();
        }
    }

    /** Drops the in-memory access token and sender so the next send re-reads the saved connection. */
    public void invalidate() {
        cachedAccess = null;
        cachedSender = null;
    }
}

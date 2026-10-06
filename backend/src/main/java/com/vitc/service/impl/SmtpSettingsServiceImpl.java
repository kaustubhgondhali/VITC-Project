package com.vitc.service.impl;

import com.vitc.dto.request.SmtpProviderDetectRequest;
import com.vitc.dto.request.SmtpSettingsRequest;
import com.vitc.dto.request.TestEmailRequest;
import com.vitc.dto.response.GoogleConnectionResponse;
import com.vitc.dto.response.SmtpProviderDetectResponse;
import com.vitc.dto.response.SmtpProviderResponse;
import com.vitc.dto.response.SmtpSettingsResponse;
import com.vitc.entity.SmtpSetting;
import com.vitc.entity.enums.SmtpSecurityMode;
import com.vitc.exception.BadRequestException;
import com.vitc.mail.MailProviderRegistry;
import com.vitc.mail.MailProviderRegistry.MailProvider;
import com.vitc.mail.MxResolver;
import com.vitc.mail.google.GmailOAuthService;
import com.vitc.mail.google.GoogleAuthException;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.security.CurrentUser;
import com.vitc.security.CurrentUserContext;
import com.vitc.service.AuditLogService;
import com.vitc.service.EmailService;
import com.vitc.service.SmtpSettingsService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Owns the single {@code smtp_settings} row. The password is encrypted on
 * the way in and only ever decrypted server side (by the mail-sending code,
 * or here only to check it is still readable) — it is never returned through
 * any API response, never logged and never written to the audit log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SmtpSettingsServiceImpl implements SmtpSettingsService {

    private static final String KEY = "DEFAULT";
    private static final String MASK = "••••••••••••";
    private static final String ENTITY = "SmtpSetting";

    private final SmtpSettingRepository repository;
    private final CryptoService crypto;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final MxResolver mxResolver;
    private final GmailOAuthService gmailOAuth;

    /** Installation default sender name, used when the admin leaves Sender Name blank. */
    @Value("${app.mail.from-name:VITC}")
    private String configuredSenderName;

    @Override
    public SmtpSettingsResponse getSettings() {
        return toResponse(current());
    }

    @Override
    @Transactional
    public SmtpSettingsResponse save(SmtpSettingsRequest request) {
        SmtpSetting s = current();
        boolean created = s.getId() == null;

        String host = request.host().trim().toLowerCase(Locale.ROOT);
        String fromEmail = request.fromEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        // An explicit provider (the Email Configuration screen always sends one) is validated
        // strictly; older callers that send no provider keep the original, permissive behaviour.
        boolean explicitProvider = notBlank(request.provider())
                && !MailProviderRegistry.AUTO.equalsIgnoreCase(request.provider().trim());
        MailProvider provider = explicitProvider
                ? MailProviderRegistry.byCode(request.provider())
                        .orElseThrow(() -> new BadRequestException("Unknown email provider"))
                : MailProviderRegistry.byHost(host).orElse(MailProviderRegistry.custom());

        SmtpSecurityMode security = notBlank(request.security())
                ? SmtpSecurityMode.valueOf(request.security().trim().toUpperCase(Locale.ROOT))
                : SmtpSecurityMode.derivedFromPort(request.port());
        validateTransport(security, request.port());

        String password = trimToNull(request.password());
        if (explicitProvider && !provider.isCustom()) {
            validateAgainstProvider(provider, host, request.port(), security, username, fromEmail);
            if (password != null && provider.credentialLength() != null) {
                // Google shows App Passwords in groups of four ("abcd efgh ijkl mnop").
                password = password.replaceAll("\\s+", "");
                if (!password.matches("^[A-Za-z0-9]{" + provider.credentialLength() + "}$")) {
                    throw new BadRequestException(provider.credentialLabel() + " must be exactly "
                            + provider.credentialLength() + " letters (spaces are ignored). Create one in your "
                            + "Google Account -> Security -> 2-Step Verification -> App passwords.");
                }
            }
        }

        boolean hadCredential = notBlank(s.getEncryptedPassword());
        // With a connected Google account no SMTP credential is needed: the SMTP fields are kept
        // only as an optional fallback, so sender details can be saved without an App Password.
        boolean google = s.googleConnected();
        if (password == null && !hadCredential && !google) {
            throw new BadRequestException(
                    "SMTP password / app password is required the first time you configure email");
        }
        if (password == null && hadCredential && !credentialReadable(s) && !google) {
            throw new BadRequestException("The saved credential can no longer be decrypted (the server "
                    + "encryption key changed). Use Replace Credential to enter it again and save.");
        }
        // Keeping a saved credential that cannot be this provider's credential would only fail at
        // Gmail with "authentication failed" - ask for a real one now instead.
        if (password == null && hadCredential && !google && explicitProvider
                && !credentialMatchesProvider(s, provider)) {
            throw new BadRequestException("The saved credential is not a valid " + provider.credentialLength()
                    + "-character " + provider.credentialLabel() + ". Click Replace Credential, enter the "
                    + provider.credentialLabel() + " and save.");
        }

        s.setHost(host);
        s.setPort(request.port());
        s.setUsername(username);
        s.setFromEmail(fromEmail);
        s.setFromName(notBlank(request.fromName()) ? request.fromName().trim() : defaultSenderName());
        s.setProvider(provider.code());
        s.setSecurityMode(security);
        s.setReplyToEmail(lowerOrNull(request.replyToEmail()));
        s.setSendingDomain(lowerOrNull(request.sendingDomain()));
        s.setDkimSelector(trimToNull(request.dkimSelector()));
        if (request.enabled() != null) {
            s.setEnabled(request.enabled());
        } else if (s.getEnabled() == null) {
            s.setEnabled(Boolean.TRUE);
        }
        s.setUpdatedBy(actor());
        boolean credentialReplaced = password != null && hadCredential;
        if (password != null) {
            s.setEncryptedPassword(crypto.encrypt(password));
        }
        s = repository.save(s);

        // Audit descriptions carry connection metadata only - never the credential.
        String summary = provider.label() + " via " + host + ":" + s.getPort() + " " + security
                + " as " + fromEmail + ", sending " + (s.isSendingEnabled() ? "enabled" : "disabled");
        auditLogService.log(created ? "SMTP_CONFIGURATION_CREATED" : "SMTP_CONFIGURATION_UPDATED",
                ENTITY, String.valueOf(s.getId()), summary);
        if (credentialReplaced) {
            auditLogService.log("SMTP_CREDENTIAL_REPLACED", ENTITY, String.valueOf(s.getId()),
                    "SMTP credential replaced for " + username);
        }
        log.info("SMTP configuration {} by {}: {}", created ? "created" : "updated", s.getUpdatedBy(), summary);
        return toResponse(s);
    }

    /** Not transactional, so the success/failure audit record commits even when the send fails. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendTestEmail(TestEmailRequest request) {
        SmtpSetting s = current();
        MailProvider provider = providerOf(s);
        String html = s.googleConnected() ? googleTestEmailHtml(s) : testEmailHtml(s, provider);
        String id = s.getId() == null ? null : String.valueOf(s.getId());
        String to = request.to().trim();
        try {
            // Uses exactly the saved row (credential decrypted in memory only for this send).
            // Connection, TLS, authentication and delivery failures come back as safe messages.
            emailService.sendHtmlWithSavedSettings(to, "VITC SMTP Configuration Test", html);
        } catch (RuntimeException ex) {
            // A short reason only: the full user-facing message mentions the credential by name,
            // which the audit sanitizer (correctly) redacts into an unreadable line.
            String reason = ex.getMessage() == null ? "error"
                    : ex.getMessage().contains("authentication") ? "the mail server rejected the sign-in"
                    : ex.getMessage().contains("No SMTP configuration") ? "nothing is saved yet"
                    : ex.getMessage().contains("Google") ? "Gmail API error"
                    : "delivery error";
            auditLogService.log("SMTP_CONFIGURATION_TESTED", ENTITY, id,
                    "Test email to " + to + " failed - " + reason);
            throw ex;
        }
        auditLogService.log("SMTP_CONFIGURATION_TESTED", ENTITY, id, "Test email to " + to + " succeeded");
    }

    @Override
    public List<SmtpProviderResponse> providers() {
        return MailProviderRegistry.all().stream()
                .map(p -> new SmtpProviderResponse(p.code(), p.label(), p.smtpHost(), p.smtpPort(),
                        p.security().name(), p.usernameRule().name(), p.credentialLabel(),
                        p.credentialStatusLabel(), p.credentialLength(), p.instructions(), p.credentialSetupUrl()))
                .toList();
    }

    @Override
    public SmtpProviderDetectResponse detectProvider(SmtpProviderDetectRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String domain = MailProviderRegistry.domainOf(email);
        String wanted = notBlank(request.provider()) ? request.provider().trim().toUpperCase(Locale.ROOT)
                : MailProviderRegistry.AUTO;

        MailProvider p;
        String method;
        if (!MailProviderRegistry.AUTO.equals(wanted)) {
            p = MailProviderRegistry.byCode(wanted).orElseThrow(() -> new BadRequestException("Unknown email provider"));
            method = "MANUAL";
        } else {
            Optional<MailProvider> byDomain = MailProviderRegistry.byDomain(domain);
            if (byDomain.isPresent()) {
                p = byDomain.get();
                method = "DOMAIN";
            } else {
                Optional<MailProvider> byMx = MailProviderRegistry.byMx(mxResolver.lookup(domain));
                p = byMx.orElse(MailProviderRegistry.custom());
                method = byMx.isPresent() ? "MX" : "NONE";
            }
        }

        boolean detected = !p.isCustom();
        String username = p.usernameRule() == MailProviderRegistry.UsernameRule.FULL_EMAIL ? email : null;
        String message;
        if (p.isCustom()) {
            message = "MANUAL".equals(method)
                    ? "Custom SMTP selected - enter the host, port and security given by your email host."
                    : "The email provider for @" + domain + " could not be determined automatically. Select "
                            + "your provider, or enter the SMTP host, port and security from your email host.";
        } else if ("MANUAL".equals(method)) {
            message = "Standard " + p.label() + " SMTP settings loaded. Enter your "
                    + p.credentialLabel() + " to authenticate.";
        } else {
            message = "Email provider detected: " + p.label()
                    + ("MX".equals(method) ? " (from the mail records of " + domain + ")" : "")
                    + ". SMTP settings loaded - only your " + p.credentialLabel() + " is still needed.";
        }
        // DKIM: only a selector whose key really is published for this domain is suggested.
        String dkimSelector = p.dkimSelectors().stream()
                .filter(sel -> mxResolver.hasDkimRecord(domain, sel))
                .findFirst().orElse(null);
        return new SmtpProviderDetectResponse(detected || "MANUAL".equals(method), p.code(), p.label(), method,
                email, p.smtpHost(), p.smtpPort(), p.security().name(), username, p.credentialLabel(),
                p.credentialLength(), p.instructions(), message,
                defaultSenderName(), email, domain.isEmpty() ? null : domain, dkimSelector);
    }

    @Override
    public GoogleConnectionResponse googleStatus() {
        return gmailOAuth.status();
    }

    @Override
    public String googleAuthorizationUrl(String loginHint) {
        CurrentUser user = CurrentUserContext.get();
        if (user == null || user.id() == null) {
            throw new BadRequestException("Sign in as Main Admin first.");
        }
        String hint = loginHint == null ? null : loginHint.trim().toLowerCase(Locale.ROOT);
        if (hint != null && !hint.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            hint = null;
        }
        try {
            return gmailOAuth.authorizationUrl(user.id(), hint);
        } catch (GoogleAuthException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Read-write: the class default is read-only, which would silently drop the disconnect. */
    @Override
    @Transactional
    public String googleDisconnect() {
        try {
            return gmailOAuth.disconnect(actor());
        } catch (GoogleAuthException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */

    /** STARTTLS and implicit TLS each have their own port; mixing them never connects. */
    private static void validateTransport(SmtpSecurityMode security, Integer port) {
        if (security == SmtpSecurityMode.STARTTLS && port == 465) {
            throw new BadRequestException("Port 465 uses SSL/TLS from the start - choose SSL/TLS security, "
                    + "or use port 587 with STARTTLS.");
        }
        if (security == SmtpSecurityMode.SSL_TLS && (port == 587 || port == 25)) {
            throw new BadRequestException("Port " + port + " uses STARTTLS - choose STARTTLS security, "
                    + "or use port 465 with SSL/TLS.");
        }
    }

    private static void validateAgainstProvider(MailProvider p, String host, Integer port, SmtpSecurityMode security,
                                                String username, String fromEmail) {
        if (!p.smtpHost().equalsIgnoreCase(host)) {
            throw new BadRequestException(p.label() + " uses SMTP host " + p.smtpHost()
                    + ". Click Load Provider Settings, or choose Custom SMTP server for a different host.");
        }
        boolean standardPort = (security == SmtpSecurityMode.STARTTLS && port == 587)
                || (security == SmtpSecurityMode.SSL_TLS && port == 465);
        if (!standardPort) {
            throw new BadRequestException(p.label() + " accepts STARTTLS on port 587 or SSL/TLS on port 465.");
        }
        if (p.usernameRule() == MailProviderRegistry.UsernameRule.FULL_EMAIL && !username.contains("@")) {
            throw new BadRequestException("The SMTP username for " + p.label() + " is the full email address.");
        }
        // Gmail refuses to send as a different consumer Gmail address than the one that logged in.
        if (isConsumerGmail(username) && isConsumerGmail(fromEmail) && !username.equalsIgnoreCase(fromEmail)) {
            throw new BadRequestException("Gmail can only send as the Gmail address it signs in with. "
                    + "The SMTP email address must match the SMTP username (" + username + ").");
        }
    }

    private static boolean isConsumerGmail(String email) {
        return MailProviderRegistry.byDomain(MailProviderRegistry.domainOf(email))
                .map(p -> MailProviderRegistry.GMAIL.equals(p.code())).orElse(false);
    }

    private SmtpSetting current() {
        return repository.findBySettingsKey(KEY).orElseGet(() -> SmtpSetting.builder()
                .settingsKey(KEY)
                .build());
    }

    private static MailProvider providerOf(SmtpSetting s) {
        return MailProviderRegistry.byCode(s.getProvider())
                .or(() -> MailProviderRegistry.byHost(s.getHost()))
                .orElse(MailProviderRegistry.custom());
    }

    /**
     * True when the saved credential has the shape the provider requires (e.g. a 16-character
     * Google App Password). Decrypted in memory only; the result is a boolean, never the value.
     */
    private boolean credentialMatchesProvider(SmtpSetting s, MailProvider provider) {
        if (provider.credentialLength() == null) {
            return true;
        }
        try {
            String plain = crypto.decrypt(s.getEncryptedPassword());
            return plain != null && plain.replaceAll("\\s+", "")
                    .matches("^[A-Za-z0-9]{" + provider.credentialLength() + "}$");
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean credentialReadable(SmtpSetting s) {
        if (!notBlank(s.getEncryptedPassword())) {
            return false;
        }
        try {
            String plain = crypto.decrypt(s.getEncryptedPassword());
            return plain != null && !plain.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private String defaultSenderName() {
        String name = configuredSenderName == null ? "" : configuredSenderName.replaceAll("[\\r\\n<>]", "").trim();
        return name.isEmpty() ? "VITC" : name;
    }

    private static String actor() {
        CurrentUser user = CurrentUserContext.get();
        return user == null ? null : user.identifier();
    }

    private SmtpSettingsResponse toResponse(SmtpSetting s) {
        MailProvider provider = providerOf(s);
        boolean credentialConfigured = notBlank(s.getEncryptedPassword());
        boolean readable = credentialConfigured && credentialReadable(s);
        boolean credentialValid = readable && credentialMatchesProvider(s, provider);
        boolean smtpConfigured = s.getHost() != null && s.getUsername() != null
                && s.getFromEmail() != null && credentialConfigured;
        boolean google = s.googleConnected();
        boolean googleUsable = google && gmailOAuth.configured();
        boolean configured = smtpConfigured || google;
        boolean enabled = s.isSendingEnabled();
        return new SmtpSettingsResponse(
                s.getId() == null ? null : provider.code(),
                s.getId() == null ? null : provider.label(),
                s.getHost(),
                s.getPort(),
                s.getHost() == null ? null : s.effectiveSecurityMode().name(),
                s.getUsername(),
                s.getFromEmail(),
                s.getFromName(),
                s.getReplyToEmail(),
                s.getSendingDomain(),
                s.getDkimSelector(),
                credentialConfigured,
                readable,
                credentialValid,
                provider.credentialLabel(),
                credentialConfigured ? provider.credentialStatusLabel() : null,
                credentialConfigured,
                credentialConfigured ? MASK : null,
                enabled,
                configured,
                enabled && (googleUsable || (smtpConfigured && credentialValid)),
                google ? SmtpSetting.AUTH_GMAIL_OAUTH : "SMTP",
                google,
                google ? s.getGoogleEmail() : null,
                s.getUpdatedBy(),
                s.getUpdatedAt());
    }

    private static String testEmailHtml(SmtpSetting s, MailProvider provider) {
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return "<!doctype html><html><body style=\"font-family:Arial,sans-serif;color:#1f2937\">"
                + "<h2 style=\"margin:0 0 12px\">VITC SMTP Configuration Test</h2>"
                + "<p>Your VITC SMTP configuration is working correctly.</p>"
                + "<table cellpadding=\"6\" style=\"border-collapse:collapse;font-size:14px\">"
                + row("Provider", provider.label())
                + row("SMTP Host", s.getHost())
                + row("SMTP Port", s.getPort() == null ? "" : String.valueOf(s.getPort()))
                + row("Security", s.getHost() == null ? "" : s.effectiveSecurityMode() == SmtpSecurityMode.SSL_TLS
                        ? "SSL/TLS" : "STARTTLS")
                + row("Sender", (s.getFromName() == null ? "" : s.getFromName() + " ") + "<" + s.getFromEmail() + ">")
                + (notBlank(s.getReplyToEmail()) ? row("Reply-To", s.getReplyToEmail()) : "")
                + row("Sent", when)
                + "</table>"
                + "<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">Sent from VITC Main Admin -> "
                + "Email / SMTP Settings.</p>"
                + "</body></html>";
    }

    private static String googleTestEmailHtml(SmtpSetting s) {
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return "<!doctype html><html><body style=\"font-family:Arial,sans-serif;color:#1f2937\">"
                + "<h2 style=\"margin:0 0 12px\">VITC Email Configuration Test</h2>"
                + "<p>Your VITC email configuration is working correctly.</p>"
                + "<table cellpadding=\"6\" style=\"border-collapse:collapse;font-size:14px\">"
                + row("Provider", "Gmail / Google Workspace")
                + row("Method", "Connected Google account (Gmail API, send-only permission)")
                + row("Sender", (s.getFromName() == null ? "" : s.getFromName() + " ") + "<" + s.getGoogleEmail() + ">")
                + (notBlank(s.getReplyToEmail()) ? row("Reply-To", s.getReplyToEmail()) : "")
                + row("Sent", when)
                + "</table>"
                + "<p style=\"color:#6b7280;font-size:12px;margin-top:16px\">Sent from VITC Main Admin -> "
                + "Email / SMTP Settings.</p>"
                + "</body></html>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"color:#6b7280\">" + HtmlUtils.htmlEscape(label) + "</td><td><strong>"
                + HtmlUtils.htmlEscape(value == null ? "" : value) + "</strong></td></tr>";
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String lowerOrNull(String v) {
        String t = trimToNull(v);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}

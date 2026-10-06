package com.vitc.service.impl;

import com.vitc.entity.SmtpSetting;
import com.vitc.entity.enums.SmtpSecurityMode;
import com.vitc.exception.BadRequestException;
import com.vitc.mail.MailProviderRegistry;
import com.vitc.mail.google.GmailOAuthService;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.service.EmailService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Low level SMTP sender - the single path every VITC email goes through
 * (student credentials, password-reset OTPs and codes, admin SMTP test).
 *
 * <p>Preference order, resolved fresh on every single send (no restart
 * needed after Main Admin saves new settings):</p>
 * <ol>
 *   <li>the single admin-managed row in {@code smtp_settings}
 *       (Main Admin -> Email / SMTP Settings) when it is enabled — a connected
 *       Google account (Gmail API over OAuth, no App Password) first, otherwise
 *       its SMTP settings; this is the primary, runtime-configurable source;</li>
 *   <li>the environment / {@code application.properties} configured
 *       {@link JavaMailSender} bean ({@code MAIL_HOST}/...), kept only as a
 *       fallback for installations that have not opened the Admin UI yet.</li>
 * </ol>
 * When neither is available {@link #isConfigured()} is false and callers keep their
 * existing "not configured" behaviour (e.g. the OTP is written to the server log in dev).
 *
 * <p>The SMTP password is decrypted in memory only for the duration of a
 * single send, is held only on the short-lived {@link JavaMailSenderImpl}
 * built for that send, and is never logged, never placed on an exception
 * message, and never returned to any caller.</p>
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final String SETTINGS_KEY = "DEFAULT";
    private static final String SETTINGS_HINT = "Admin -> Email / SMTP Settings";

    private final SmtpSettingRepository smtpSettingRepository;
    private final CryptoService crypto;
    private final GmailOAuthService gmailOAuth;
    private final ObjectProvider<JavaMailSender> staticMailSenderProvider;
    private final String staticHost;
    private final String staticFrom;
    private final String staticFromName;

    public EmailServiceImpl(SmtpSettingRepository smtpSettingRepository,
                            CryptoService crypto,
                            GmailOAuthService gmailOAuth,
                            ObjectProvider<JavaMailSender> staticMailSenderProvider,
                            @Value("${spring.mail.host:}") String staticHost,
                            @Value("${app.mail.from:}") String staticFrom,
                            @Value("${app.mail.from-name:VITC}") String staticFromName) {
        this.smtpSettingRepository = smtpSettingRepository;
        this.crypto = crypto;
        this.gmailOAuth = gmailOAuth;
        this.staticMailSenderProvider = staticMailSenderProvider;
        this.staticHost = staticHost == null ? "" : staticHost.trim();
        this.staticFrom = staticFrom == null ? "" : staticFrom.trim();
        this.staticFromName = staticFromName == null || staticFromName.isBlank()
                ? "VITC" : staticFromName.trim();
    }

    @Override
    public boolean isConfigured() {
        return resolve() != null;
    }

    @Override
    public void sendHtml(String to, String subject, String html) {
        requireRecipient(to);
        ResolvedMailConfig cfg = resolve();
        if (cfg == null) {
            throw new BadRequestException("Email is not configured. Set it in " + SETTINGS_HINT + ".");
        }
        send(cfg, to, subject, html);
    }

    @Override
    public void sendHtmlWithSavedSettings(String to, String subject, String html) {
        requireRecipient(to);
        SmtpSetting s = smtpSettingRepository.findBySettingsKey(SETTINGS_KEY).orElse(null);
        ResolvedMailConfig google = fromGoogle(s);
        if (google != null) {
            send(google, to, subject, html);
            return;
        }
        if (s == null || !isUsable(s)) {
            throw new BadRequestException(
                    "No SMTP configuration is saved yet. Save the configuration first, then send the test email.");
        }
        ResolvedMailConfig cfg = fromDb(s);
        if (cfg == null) {
            throw new BadRequestException("The saved SMTP credential can no longer be decrypted. "
                    + "Use Replace Credential to enter it again, save, and retry.");
        }
        send(cfg, to, subject, html);
    }

    @Override
    public Optional<String> ownerInbox() {
        SmtpSetting s = smtpSettingRepository.findBySettingsKey(SETTINGS_KEY).orElse(null);
        if (s != null && s.isSendingEnabled()) {
            if (notBlank(s.getReplyToEmail())) {
                return Optional.of(s.getReplyToEmail().trim());
            }
            if (s.googleConnected() && notBlank(s.getGoogleEmail())) {
                return Optional.of(s.getGoogleEmail().trim());
            }
            if (notBlank(s.getFromEmail())) {
                return Optional.of(s.getFromEmail().trim());
            }
        }
        return staticFrom.isBlank() ? Optional.empty() : Optional.of(staticFrom);
    }

    /* ------------------------------------------------------------------ */

    private static void requireRecipient(String to) {
        if (to == null || to.isBlank() || !to.contains("@") || to.contains("\r") || to.contains("\n")) {
            throw new BadRequestException("Invalid recipient email address");
        }
    }

    private void send(ResolvedMailConfig cfg, String to, String subject, String html) {
        try {
            MimeMessage message = cfg.sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(html, true);
            try {
                helper.setFrom(new InternetAddress(cfg.from, cfg.fromName, "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(cfg.from);
            }
            // Only a validated, non-blank address ever becomes a Reply-To header.
            if (notBlank(cfg.replyTo)) {
                helper.setReplyTo(cfg.replyTo);
            }
            cfg.sender.send(message);
        } catch (MailAuthenticationException ex) {
            if (cfg.oauth) {
                // GmailApiMailSender only ever carries GoogleAuthException's credential-free messages.
                throw new BadRequestException(safeGoogleMessage(ex));
            }
            throw new BadRequestException(MailProviderRegistry.GMAIL.equals(cfg.provider)
                    ? "SMTP authentication failed. Gmail rejected the username / App Password. Use a "
                            + "16-character Google App Password (not your normal Google password) and make sure "
                            + "the SMTP username is the full Gmail address. Update it in " + SETTINGS_HINT + "."
                    : "SMTP authentication failed. Check the username / password in " + SETTINGS_HINT + ".");
        } catch (MailSendException ex) {
            if (cfg.oauth) {
                throw new BadRequestException(safeGoogleMessage(ex));
            }
            throw new BadRequestException(
                    "Mail server could not deliver the message. Check the host, port "
                            + "and TLS settings in " + SETTINGS_HINT + ".");
        } catch (Exception ex) {
            // Never surface stack traces or SMTP credentials.
            log.warn("Email delivery failed ({})", ex.getClass().getSimpleName());
            throw new BadRequestException("Email could not be sent. Please verify the mail configuration.");
        }
    }

    /**
     * Resolves the mail server to use for this send. Reads the database row
     * fresh every time (deliberately not cached) so a settings change made
     * by Main Admin takes effect on the very next email, with no restart.
     */
    private ResolvedMailConfig resolve() {
        SmtpSetting s = smtpSettingRepository.findBySettingsKey(SETTINGS_KEY).orElse(null);
        if (s != null && s.isSendingEnabled()) {
            ResolvedMailConfig google = fromGoogle(s);
            if (google != null) {
                return google;
            }
        }
        if (s != null && isUsable(s) && s.isSendingEnabled()) {
            ResolvedMailConfig fromDb = fromDb(s);
            if (fromDb != null) {
                return fromDb;
            }
            // Stored row exists but its password could not be decrypted
            // (e.g. the encryption key changed) - fall through to the
            // static/env fallback below rather than failing every send.
        }
        JavaMailSender staticSender = staticMailSenderProvider.getIfAvailable();
        if (staticSender != null && !staticHost.isBlank() && !staticFrom.isBlank()) {
            return new ResolvedMailConfig(staticSender, staticFrom, staticFromName, null, null, false);
        }
        return null;
    }

    private boolean isUsable(SmtpSetting s) {
        return notBlank(s.getHost()) && s.getPort() != null && notBlank(s.getUsername())
                && notBlank(s.getEncryptedPassword()) && notBlank(s.getFromEmail());
    }

    /** Builds a short-lived sender from the admin-saved, DB-stored configuration. */
    private ResolvedMailConfig fromDb(SmtpSetting s) {
        String password;
        try {
            password = crypto.decrypt(s.getEncryptedPassword());
        } catch (Exception ex) {
            // Deliberately no password / key material in this log line.
            log.warn("Stored SMTP password could not be decrypted for this installation "
                    + "(encryption key changed?) - re-save it in " + SETTINGS_HINT);
            return null;
        }
        if (password == null || password.isBlank()) {
            return null;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.getHost().trim());
        sender.setPort(s.getPort());
        sender.setUsername(s.getUsername().trim());
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        boolean implicitTls = s.effectiveSecurityMode() == SmtpSecurityMode.SSL_TLS;
        props.put("mail.smtp.starttls.enable", implicitTls ? "false" : "true");
        props.put("mail.smtp.ssl.enable", implicitTls ? "true" : "false");
        if (s.getSecurityMode() != null) {
            // Explicitly chosen security (Email Configuration screen): never fall back to a
            // cleartext session that would send the credential unencrypted, and verify the
            // server certificate matches the host. Legacy rows keep their original behaviour.
            if (!implicitTls) {
                props.put("mail.smtp.starttls.required", "true");
            }
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        String fromName = notBlank(s.getFromName()) ? s.getFromName().trim() : "VITC";
        return new ResolvedMailConfig(sender, s.getFromEmail().trim(), fromName,
                notBlank(s.getReplyToEmail()) ? s.getReplyToEmail().trim() : null, s.getProvider(), false);
    }

    /** Sender for a connected Google account: Gmail API, From = the connected address (Gmail requires it). */
    private ResolvedMailConfig fromGoogle(SmtpSetting s) {
        if (s == null || !s.googleConnected()) {
            return null;
        }
        return gmailOAuth.senderFor(s).map(sender -> {
            String from = notBlank(s.getGoogleEmail()) ? s.getGoogleEmail().trim() : s.getFromEmail();
            String fromName = notBlank(s.getFromName()) ? s.getFromName().trim() : "VITC";
            return new ResolvedMailConfig(sender, from, fromName,
                    notBlank(s.getReplyToEmail()) ? s.getReplyToEmail().trim() : null, MailProviderRegistry.GMAIL, true);
        }).filter(c -> notBlank(c.from)).orElse(null);
    }

    /** The Gmail/OAuth failure message - these are written to be credential-free by GoogleAuthException. */
    private static String safeGoogleMessage(Exception ex) {
        Throwable t = ex;
        if (ex instanceof MailSendException mse && !mse.getFailedMessages().isEmpty()) {
            t = mse.getFailedMessages().values().iterator().next();
        }
        while (t != null && !(t instanceof com.vitc.mail.google.GoogleAuthException)) {
            t = t.getCause();
        }
        return t != null ? t.getMessage() : "Gmail could not send the message. Please reconnect the Google account "
                + "in " + SETTINGS_HINT + ".";
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    /** Everything needed to send one email: which sender, and the From / Reply-To identity to use. */
    private static final class ResolvedMailConfig {
        final JavaMailSender sender;
        final String from;
        final String fromName;
        final String replyTo;
        final String provider;
        /** True when sending through the Gmail API with a connected Google account. */
        final boolean oauth;

        ResolvedMailConfig(JavaMailSender sender, String from, String fromName, String replyTo, String provider,
                           boolean oauth) {
            this.sender = sender;
            this.from = from;
            this.fromName = fromName;
            this.replyTo = replyTo;
            this.provider = provider;
            this.oauth = oauth;
        }
    }
}

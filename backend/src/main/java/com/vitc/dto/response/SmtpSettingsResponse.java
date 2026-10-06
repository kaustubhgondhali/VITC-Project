package com.vitc.dto.response;

import java.time.LocalDateTime;

/**
 * Safe view of the Email / SMTP configuration. The credential (app password /
 * SMTP password) is NEVER included, in plain or encrypted form - only booleans and
 * a status label so the admin UI can show that one is stored.
 *
 * <p>{@code passwordConfigured} and {@code maskedPassword} (a fixed bullet string, not
 * derived from the credential) are kept for backward compatibility with existing
 * callers; {@code credentialConfigured} / {@code credentialStatus} are the current names.</p>
 */
public record SmtpSettingsResponse(
        String provider,
        String providerLabel,
        String host,
        Integer port,
        String security,
        String username,
        String fromEmail,
        String fromName,
        String replyToEmail,
        String sendingDomain,
        String dkimSelector,
        boolean credentialConfigured,
        /** False when a stored credential can no longer be decrypted (encryption key changed). */
        boolean credentialReadable,
        /** Readable AND the right shape for the provider (e.g. 16-character Google App Password). */
        boolean credentialValid,
        String credentialLabel,
        String credentialStatus,
        boolean passwordConfigured,
        String maskedPassword,
        boolean enabled,
        /** Every required field and a credential are saved. */
        boolean configured,
        /** Configured, readable and enabled - i.e. system emails are sent through it. */
        boolean active,
        /** SMTP, or GMAIL_OAUTH when a Google account is connected (Gmail API, no App Password). */
        String authMode,
        boolean googleConnected,
        String googleEmail,
        String updatedBy,
        LocalDateTime updatedAt) {
}

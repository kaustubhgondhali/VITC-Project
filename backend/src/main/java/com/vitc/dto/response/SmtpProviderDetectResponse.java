package com.vitc.dto.response;

import java.util.List;

/**
 * Result of "Load Provider Settings". {@code method} says how the provider was found:
 * DOMAIN (public mailbox domain), MX (the domain's mail records), MANUAL (picked in the
 * dropdown) or NONE (not determined - host/port are then left empty, never guessed).
 */
public record SmtpProviderDetectResponse(
        boolean detected,
        String provider,
        String providerLabel,
        String method,
        String email,
        String host,
        Integer port,
        String security,
        String username,
        String credentialLabel,
        Integer credentialLength,
        List<String> instructions,
        String message,
        /* Suggested sender details for the Email Configuration screen (filled only into empty fields). */
        String senderName,
        String replyToEmail,
        String sendingDomain,
        /** A selector whose DKIM record was actually found in DNS, or null when none was found. */
        String dkimSelector) {
}

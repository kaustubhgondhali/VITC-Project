package com.vitc.dto.response;

import java.util.List;

/** One entry of the provider dropdown, straight from {@code MailProviderRegistry}. */
public record SmtpProviderResponse(
        String code,
        String label,
        String host,
        Integer port,
        String security,
        String usernameRule,
        String credentialLabel,
        String credentialStatusLabel,
        Integer credentialLength,
        List<String> instructions,
        /** Provider page where the credential (e.g. Google App Password) is created, or null. */
        String credentialSetupUrl) {
}

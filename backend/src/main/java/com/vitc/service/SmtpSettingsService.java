package com.vitc.service;

import com.vitc.dto.request.SmtpProviderDetectRequest;
import com.vitc.dto.request.SmtpSettingsRequest;
import com.vitc.dto.request.TestEmailRequest;
import com.vitc.dto.response.GoogleConnectionResponse;
import com.vitc.dto.response.SmtpProviderDetectResponse;
import com.vitc.dto.response.SmtpProviderResponse;
import com.vitc.dto.response.SmtpSettingsResponse;
import java.util.List;

/**
 * Owns the single, admin-managed SMTP configuration row. The password is
 * always encrypted at rest and is never returned to any caller.
 */
public interface SmtpSettingsService {

    /** Current settings, safe for the admin UI (no password). */
    SmtpSettingsResponse getSettings();

    /** Saves (creates or updates) the SMTP configuration. */
    SmtpSettingsResponse save(SmtpSettingsRequest request);

    /** Sends a real SMTP test message using the saved Main Admin settings. */
    void sendTestEmail(TestEmailRequest request);

    /** The provider dropdown, from {@code MailProviderRegistry}. */
    List<SmtpProviderResponse> providers();

    /** "Load Provider Settings": SMTP email address (+ optional provider) -> standard settings. */
    SmtpProviderDetectResponse detectProvider(SmtpProviderDetectRequest request);

    /** "Connect Google Account" status - never a token. */
    GoogleConnectionResponse googleStatus();

    /** Google authorization URL for the signed-in Main Admin (optionally pre-selecting an address). */
    String googleAuthorizationUrl(String loginHint);

    /** Revokes and removes the Google connection; SMTP settings are kept as the fallback. */
    String googleDisconnect();
}

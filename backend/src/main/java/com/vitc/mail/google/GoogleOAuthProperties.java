package com.vitc.mail.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google Cloud OAuth client settings for "Connect Google Account". They come from the
 * environment (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / GOOGLE_REDIRECT_URI, also readable from
 * backend/.env) - never from source code - and the secret is never returned by any API or logged.
 */
@Component
public class GoogleOAuthProperties {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthProperties(@Value("${app.google.client-id:}") String clientId,
                                 @Value("${app.google.client-secret:}") String clientSecret,
                                 @Value("${app.google.redirect-uri:}") String redirectUri) {
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.redirectUri = trim(redirectUri);
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    /** Must match an "Authorized redirect URI" on the OAuth client in Google Cloud, character for character. */
    public String redirectUri() {
        return redirectUri;
    }

    /** True once all three values are present, so the screen can say what is missing. */
    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !redirectUri.isEmpty();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /** Never print the secret. */
    @Override
    public String toString() {
        return "GoogleOAuthProperties[clientId=" + clientId + ", clientSecret=***, redirectUri=" + redirectUri + "]";
    }
}

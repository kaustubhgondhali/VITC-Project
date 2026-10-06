package com.vitc.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safe "Connect Google Account" status. Never carries a token, an authorization code or the
 * OAuth client secret. {@code redirectUri} is public information (it is registered in Google
 * Cloud) and is shown so the admin knows exactly what to register.
 */
public record GoogleConnectionResponse(
        /** The server has GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / GOOGLE_REDIRECT_URI. */
        boolean configured,
        boolean connected,
        String email,
        LocalDateTime connectedAt,
        String connectedBy,
        String redirectUri,
        /** Names of the environment variables still missing (names only, never values). */
        List<String> missing) {
}

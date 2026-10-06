package com.vitc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * PART 2B-3/7 - Authentication & Token/Session Safety.
 *
 * <p>Session tokens and password-reset tokens were previously compared with
 * {@link String#equals(Object)}, which short-circuits on the first mismatched
 * character. For a secret value compared against attacker-controlled input,
 * that timing difference is a classic (if narrow) side channel. This helper
 * performs the same "does this token match the one on the account" check in
 * constant time, without changing the token format, storage, generation, or
 * expiry logic anywhere in the codebase.
 *
 * <p>This does not replace or redesign the existing opaque-session-token
 * model - it only hardens the equality check every interceptor/service
 * already performs.</p>
 */
public final class TokenSecurityUtil {

    private TokenSecurityUtil() {
    }

    /**
     * Constant-time equality check for secret tokens (session tokens, reset
     * tokens/codes). Returns {@code false} for any {@code null} input instead
     * of throwing, matching the previous {@code equals()} call sites.
     */
    public static boolean matches(String stored, String candidate) {
        if (stored == null || candidate == null) {
            return false;
        }
        byte[] a = stored.getBytes(StandardCharsets.UTF_8);
        byte[] b = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}

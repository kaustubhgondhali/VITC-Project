package com.vitc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PART 6C-2A/8 - mints and verifies short-lived, signed tokens that scope a
 * single lesson's local video file to a single student for a limited time.
 *
 * <p>Why this exists: a plain HTML {@code <video>} element cannot attach the
 * {@code X-Student-Id} / {@code X-Student-Token} headers that every other
 * {@code /api/v1/student/**} call uses ({@link StudentAuthInterceptor}), so
 * the browser cannot authenticate a direct video request the same way it
 * authenticates a JSON API call. Rather than inventing a parallel login
 * system, this service issues a narrow, single-purpose capability token -
 * {@code studentId.lessonId.expiry} signed with HMAC-SHA256 - and ONLY after
 * {@code StudentLearningServiceImpl.lesson()} has already run the full
 * Authentication -> Role -> Enrollment -> Lesson-access chain for that exact
 * student and lesson. The token simply carries the result of that check
 * forward to the one request a browser cannot attach headers to; it does not
 * grant anything the session itself did not already grant, and it expires
 * quickly.</p>
 *
 * <p>The signing key comes from {@code VIDEO_TOKEN_SECRET} (mapped to
 * {@code app.security.video-token-key}). A development-only fallback is used
 * when it is not set, matching the existing {@code CryptoService} pattern, so
 * local/dev setups keep working without extra configuration.</p>
 */
@Slf4j
@Component
public class VideoAccessTokenService {

    private static final String ALGO = "HmacSHA256";
    private static final String DEV_FALLBACK = "vitc-dev-only-video-token-key-change-me";

    private final SecretKeySpec key;
    private final long ttlSeconds;

    public VideoAccessTokenService(
            @Value("${app.security.video-token-key:}") String configuredKey,
            @Value("${app.video.stream-token-ttl-minutes:240}") long ttlMinutes) {
        String material = configuredKey == null ? "" : configuredKey.trim();
        if (material.isEmpty()) {
            log.warn("VIDEO_TOKEN_SECRET is not set - falling back to a development key. "
                    + "Set it before deploying course videos to production.");
            material = DEV_FALLBACK;
        }
        this.key = new SecretKeySpec(material.getBytes(StandardCharsets.UTF_8), ALGO);
        this.ttlSeconds = ttlMinutes * 60;
    }

    /** Issues a token scoping {@code lessonId}'s local video to {@code studentId} for a limited time. */
    public String issue(Long studentId, Long lessonId) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = studentId + "." + lessonId + "." + expiresAt;
        String signature = sign(payload);
        return urlSafe(payload) + "." + urlSafe(signature);
    }

    /** Signature-verified, not-yet-expiry-checked contents of a token. */
    public record ParsedToken(long studentId, long lessonId, long expiresAt) {
        public boolean isExpired() {
            return Instant.now().getEpochSecond() > expiresAt;
        }
    }

    /**
     * Verifies the token's signature and structure. Returns {@link ParsedToken}
     * only for a signature that actually matches this server's key - anything
     * malformed or tampered with returns empty. Expiry and the lessonId the
     * caller is asking for are checked separately by the caller, since this
     * layer alone cannot know which lesson the request is for.
     */
    public java.util.Optional<ParsedToken> parse(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot < 0 || token.indexOf('.', dot + 1) >= 0) {
            return java.util.Optional.empty();
        }
        String payload;
        String providedSignature;
        try {
            payload = fromUrlSafe(token.substring(0, dot));
            providedSignature = fromUrlSafe(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
        String expectedSignature = sign(payload);
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            return java.util.Optional.empty();
        }
        String[] fields = payload.split("\\.");
        if (fields.length != 3) {
            return java.util.Optional.empty();
        }
        try {
            long studentId = Long.parseLong(fields[0]);
            long lessonId = Long.parseLong(fields[1]);
            long expiresAt = Long.parseLong(fields[2]);
            return java.util.Optional.of(new ParsedToken(studentId, lessonId, expiresAt));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(key);
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign video access token", e);
        }
    }

    private static String urlSafe(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String fromUrlSafe(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}

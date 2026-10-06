package com.vitc.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PART 2A/7 - central, externally-tunable limits for every rate-limited
 * endpoint. Nothing below is a real secret; these are safe to keep as
 * defaults and override per-environment via app.rate-limit.* properties
 * (or the matching RATE_LIMIT_* environment variables) without touching code.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Master switch - set app.rate-limit.enabled=false to disable (e.g. in tests). */
    private boolean enabled = true;

    /** Login attempts (student / teacher / admin): ~5 per minute per IP. */
    private Bucket login = new Bucket(5, 60);

    /** Forgot-password requests: ~3 per 10 minutes per account identifier. */
    private Bucket forgotPassword = new Bucket(3, 600);

    /** Reset-password / change-password attempts: per IP. */
    private Bucket resetPassword = new Bucket(5, 600);

    /** Publicly-writable content forms (contact message, review submission): per IP. */
    private Bucket publicWrite = new Bucket(5, 600);

    /** Payment-related endpoints (checkout order create/pay/confirm): per IP. */
    private Bucket payment = new Bucket(10, 600);

    @Getter
    @Setter
    public static class Bucket {
        /** Max allowed calls within the window. */
        private int maxAttempts;
        /** Window length in seconds. */
        private int windowSeconds;

        public Bucket() {
        }

        public Bucket(int maxAttempts, int windowSeconds) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
        }
    }
}

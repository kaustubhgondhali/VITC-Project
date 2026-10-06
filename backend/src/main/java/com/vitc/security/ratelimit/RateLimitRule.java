package com.vitc.security.ratelimit;

import java.util.function.Function;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * PART 2A/7 - one centrally-declared abuse-protection rule: which requests
 * it applies to, how the caller is identified, and which configurable
 * {@link RateLimitProperties.Bucket} governs it. Adding protection to a new
 * endpoint means adding one rule here, not touching a controller.
 */
public class RateLimitRule {

    /** How the caller is identified for this rule's counter. */
    public enum KeyType {
        /** Client IP only. */
        IP,
        /** Client IP combined with an account identifier pulled from the JSON body (falls back to IP alone). */
        IP_AND_IDENTIFIER
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final HttpMethod method;
    private final String pathPattern;
    private final KeyType keyType;
    private final Function<RateLimitProperties, RateLimitProperties.Bucket> bucket;
    private final String label;

    public RateLimitRule(HttpMethod method, String pathPattern, KeyType keyType,
            Function<RateLimitProperties, RateLimitProperties.Bucket> bucket, String label) {
        this.method = method;
        this.pathPattern = pathPattern;
        this.keyType = keyType;
        this.bucket = bucket;
        this.label = label;
    }

    public boolean matches(String requestMethod, String requestPath) {
        return this.method.name().equalsIgnoreCase(requestMethod) && MATCHER.match(pathPattern, requestPath);
    }

    public KeyType keyType() {
        return keyType;
    }

    public RateLimitProperties.Bucket bucket(RateLimitProperties props) {
        return bucket.apply(props);
    }

    public String label() {
        return label;
    }
}

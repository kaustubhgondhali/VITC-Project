package com.vitc.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * PART 2C-1/7 - reads the client IP and User-Agent off the current HTTP
 * request for audit logging.
 *
 * <p>Deliberately independent from {@code RateLimitingFilter}'s own IP
 * resolution so this new code path can never change existing rate-limiting
 * behavior. Safe to call from anywhere (including code with no request in
 * scope, e.g. a scheduled job) - returns {@code null} rather than throwing.</p>
 */
public final class RequestMetadataUtil {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private RequestMetadataUtil() {
    }

    /** Current request, or {@code null} if none is bound to this thread (e.g. a scheduled job). */
    public static HttpServletRequest currentRequest() {
        Object attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /** Honors a single well-formed X-Forwarded-For (trusted only when a reverse proxy sets it), else the socket address. */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > MAX_USER_AGENT_LENGTH
                ? userAgent.substring(0, MAX_USER_AGENT_LENGTH)
                : userAgent;
    }
}

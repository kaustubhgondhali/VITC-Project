package com.vitc.security.headers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PART 2B-1/7 - single, centralized place where production HTTP security headers are
 * applied to every response the backend produces (API JSON, uploaded files, Swagger UI,
 * error pages and rate-limit 429s alike).
 *
 * <p>Runs ahead of {@code RateLimitingFilter} so even a rejected/blocked request is
 * returned with the full header set. Adds headers only - never blocks, never rewrites a
 * body, and never touches existing behaviour.</p>
 *
 * <p>HSTS is environment aware: it is emitted only when the request actually arrived over
 * HTTPS (directly, or via a reverse proxy setting {@code X-Forwarded-Proto: https}), so
 * plain-HTTP local development is unaffected.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY = "Content-Security-Policy-Report-Only";

    private final SecurityHeadersProperties properties;
    private final ContentSecurityPolicyBuilder cspBuilder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (properties.isEnabled()) {
            apply(request, response);
        }
        chain.doFilter(request, response);
    }

    private void apply(HttpServletRequest request, HttpServletResponse response) {
        setIfAbsent(response, "X-Content-Type-Options", "nosniff");
        setIfAbsent(response, "X-Frame-Options", properties.getFrameOptions());
        setIfAbsent(response, "Referrer-Policy", properties.getReferrerPolicy());
        setIfAbsent(response, "Permissions-Policy", properties.getPermissionsPolicy());
        setIfAbsent(response, "Cross-Origin-Resource-Policy", "cross-origin");

        if (properties.isCspEnabled()) {
            String header = properties.isCspReportOnly() ? CSP_REPORT_ONLY : CSP;
            setIfAbsent(response, header, cspBuilder.policy());
        }

        if (properties.isHstsEnabled() && isHttps(request)) {
            StringBuilder hsts = new StringBuilder("max-age=").append(properties.getHstsMaxAgeSeconds());
            if (properties.isHstsIncludeSubDomains()) {
                hsts.append("; includeSubDomains");
            }
            if (properties.isHstsPreload()) {
                hsts.append("; preload");
            }
            setIfAbsent(response, "Strict-Transport-Security", hsts.toString());
        }
    }

    /** True only for genuine HTTPS traffic - direct TLS or behind a TLS-terminating proxy. */
    private boolean isHttps(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto == null || forwardedProto.isBlank()) {
            return false;
        }
        // A proxy chain may send a comma separated list; the client-facing value comes first.
        String first = forwardedProto.split(",")[0].trim();
        return "https".equalsIgnoreCase(first);
    }

    private void setIfAbsent(HttpServletResponse response, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}

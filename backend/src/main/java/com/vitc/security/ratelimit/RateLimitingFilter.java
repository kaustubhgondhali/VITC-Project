package com.vitc.security.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PART 2A/7 - single, centralized abuse-protection choke point. Every
 * protected endpoint is declared once in {@link RateLimitRuleRegistry};
 * nothing below is duplicated per-controller. Runs ahead of the existing
 * auth interceptors (which are MVC-level, i.e. always after servlet
 * filters), so a flood of bad login attempts never even reaches them.
 *
 * <p>Never blocks anything by itself: a request whose path/method matches no
 * rule passes straight through with no extra work.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** Candidate JSON field names that identify the account being acted on, checked in order. */
    private static final List<String> IDENTIFIER_FIELDS = List.of(
            "usernameOrEmail", "studentLoginIdOrEmail", "email", "username", "identifier");

    private static final long MAX_BODY_BYTES_TO_INSPECT = 8 * 1024; // 8 KB - these are small JSON login/forgot-password payloads only.

    private final RateLimitRuleRegistry ruleRegistry;
    private final RateLimitProperties properties;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitRule matchedRule = ruleRegistry.rules().stream()
                .filter(r -> r.matches(method, path))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        HttpServletRequest requestToContinueWith = request;
        String key = "rl:" + matchedRule.label() + ":" + clientIp;

        if (matchedRule.keyType() == RateLimitRule.KeyType.IP_AND_IDENTIFIER
                && isSmallJsonBody(request)) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            requestToContinueWith = cached;
            String identifier = extractIdentifier(cached.bodyAsString());
            if (identifier != null) {
                key = key + ":" + identifier.toLowerCase();
            }
        }

        RateLimitProperties.Bucket bucket = matchedRule.bucket(properties);
        boolean allowed = rateLimiterService.tryAcquire(key, bucket.getMaxAttempts(), bucket.getWindowSeconds());

        if (!allowed) {
            log.warn("Rate limit exceeded: rule={} ip={} path={}", matchedRule.label(), clientIp, path);
            writeTooManyRequests(response);
            return;
        }

        chain.doFilter(requestToContinueWith, response);
    }

    private boolean isSmallJsonBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        int contentLength = request.getContentLength();
        return contentType != null
                && contentType.toLowerCase().contains("application/json")
                && contentLength > 0
                && contentLength <= MAX_BODY_BYTES_TO_INSPECT;
    }

    private String extractIdentifier(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            for (String field : IDENTIFIER_FIELDS) {
                JsonNode value = node.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText().trim();
                }
            }
        } catch (Exception ex) {
            // Malformed/non-JSON body - fall back to IP-only keying, the request
            // itself is still handled normally (and will likely fail validation downstream).
            log.debug("Could not parse request body for rate-limit keying on {}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
        return null;
    }

    /** Honors a single well-formed X-Forwarded-For (trusted only when a reverse proxy sets it), else the socket address. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429); // HttpServletResponse has no named constant for 429.
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Too many requests. Please wait a moment and try again.\"}");
    }
}

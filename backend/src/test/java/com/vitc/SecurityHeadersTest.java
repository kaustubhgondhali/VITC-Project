package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;

import com.vitc.security.headers.ContentSecurityPolicyBuilder;
import com.vitc.security.headers.SecurityHeadersFilter;
import com.vitc.security.headers.SecurityHeadersProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * PART 2B-1/7 - verifies the security headers are emitted, that the CSP keeps the
 * domains the existing frontend genuinely needs, and that HSTS never leaks onto plain
 * HTTP (local development).
 */
class SecurityHeadersTest {

    private final SecurityHeadersProperties properties = new SecurityHeadersProperties();
    private final SecurityHeadersFilter filter =
            new SecurityHeadersFilter(properties, new ContentSecurityPolicyBuilder(properties));

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void addsBaselineHeadersOnEveryResponse() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/api/v1/courses"));

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(response.getHeader("Content-Security-Policy")).isNotBlank();
    }

    @Test
    void cspAllowsOnlyTheDomainsTheFrontendActuallyUses() throws Exception {
        String csp = run(new MockHttpServletRequest("GET", "/index.html"))
                .getHeader("Content-Security-Policy");

        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("https://checkout.razorpay.com");
        assertThat(csp).contains("https://fonts.googleapis.com");
        assertThat(csp).contains("https://fonts.gstatic.com");
        assertThat(csp).contains("object-src 'none'");
        assertThat(csp).contains("base-uri 'self'");
        assertThat(csp).contains("form-action 'self'");
        assertThat(csp).contains("frame-ancestors 'self'");
        // 'unsafe-eval' is not needed by any current dependency and must stay out.
        assertThat(csp).doesNotContain("'unsafe-eval'");
    }

    @Test
    void hstsIsSkippedOnPlainHttpButSentOverHttps() throws Exception {
        assertThat(run(new MockHttpServletRequest("GET", "/api/v1/courses"))
                .getHeader("Strict-Transport-Security")).isNull();

        MockHttpServletRequest secure = new MockHttpServletRequest("GET", "/api/v1/courses");
        secure.addHeader("X-Forwarded-Proto", "https");
        assertThat(run(secure).getHeader("Strict-Transport-Security"))
                .contains("max-age=31536000")
                .contains("includeSubDomains");
    }

    @Test
    void headersCanBeDisabledEntirely() throws Exception {
        properties.setEnabled(false);
        assertThat(run(new MockHttpServletRequest("GET", "/api/v1/courses"))
                .getHeader("X-Content-Type-Options")).isNull();
        properties.setEnabled(true);
    }
}

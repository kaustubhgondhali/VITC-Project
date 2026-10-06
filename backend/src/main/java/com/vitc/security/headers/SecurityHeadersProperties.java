package com.vitc.security.headers;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PART 2B-1/7 - externally tunable HTTP security headers / CSP configuration.
 *
 * <p>Nothing here is a secret. Every value is a safe production default that can be
 * overridden per environment through {@code app.security.headers.*} properties (or the
 * matching {@code SECURITY_HEADERS_*} environment variables) without touching code.</p>
 *
 * <p>The allow-lists below were derived by inspecting the actual VITC frontend
 * (see {@code deploy/SECURITY-HEADERS.md}); only domains the site genuinely uses
 * are present.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.headers")
public class SecurityHeadersProperties {

    /** Master switch - set to false to disable all header injection. */
    private boolean enabled = true;

    /** Send Content-Security-Policy. */
    private boolean cspEnabled = true;

    /**
     * Send CSP in report-only mode (nothing is blocked, violations are only reported to
     * the browser console). Useful when rolling the policy out on a new environment.
     */
    private boolean cspReportOnly = false;

    /** X-Frame-Options value; SAMEORIGIN keeps the admin/teacher preview iframes working. */
    private String frameOptions = "SAMEORIGIN";

    private String referrerPolicy = "strict-origin-when-cross-origin";

    private String permissionsPolicy =
            "geolocation=(), microphone=(), camera=(), payment=(self), usb=(), magnetometer=(), "
                    + "gyroscope=(), accelerometer=(), fullscreen=(self)";

    /**
     * Strict-Transport-Security is only ever emitted on requests that actually arrived over
     * HTTPS (directly or through a proxy that sets X-Forwarded-Proto: https). Plain HTTP
     * local development therefore never receives HSTS and can never get pinned to https.
     */
    private boolean hstsEnabled = true;

    /** 1 year, the value required for preload eligibility. */
    private long hstsMaxAgeSeconds = 31536000L;

    private boolean hstsIncludeSubDomains = true;

    /** Off by default: preload is an irreversible, domain-wide opt-in. */
    private boolean hstsPreload = false;

    /**
     * Inline {@code <script>} blocks exist on ~30 existing HTML pages (page bootstrap code)
     * and Swagger UI ships its own inline bootstrap. Removing them would mean rewriting the
     * frontend, which is explicitly out of scope, so 'unsafe-inline' stays for script-src.
     */
    private boolean allowInlineScripts = true;

    /**
     * Inline {@code style="..."} attributes and {@code <style>} blocks are used across the
     * existing pages (and by the dynamically built admin/teacher markup), so style-src keeps
     * 'unsafe-inline'. CSS cannot execute JavaScript, so the risk is far lower.
     */
    private boolean allowInlineStyles = true;

    /** 'unsafe-eval' is NOT required by any current dependency (Razorpay included). */
    private boolean allowEval = false;

    private final Csp csp = new Csp();

    @Getter
    @Setter
    public static class Csp {
        private List<String> defaultSrc = new ArrayList<>(List.of("'self'"));

        /** Razorpay Checkout is loaded dynamically by assets/js/payments.js. */
        private List<String> scriptSrc = new ArrayList<>(List.of(
                "'self'", "https://checkout.razorpay.com"));

        /** Google Fonts stylesheet is linked from the public pages. */
        private List<String> styleSrc = new ArrayList<>(List.of(
                "'self'", "https://fonts.googleapis.com"));

        private List<String> imgSrc = new ArrayList<>(List.of(
                "'self'", "data:", "blob:",
                "https://images.unsplash.com",
                "https://www.gstatic.com",
                "https://*.razorpay.com",
                "https://i.ytimg.com"));

        private List<String> fontSrc = new ArrayList<>(List.of(
                "'self'", "data:", "https://fonts.gstatic.com"));

        /** XHR/fetch targets: the backend itself plus Razorpay's API/telemetry hosts. */
        private List<String> connectSrc = new ArrayList<>(List.of(
                "'self'",
                "https://api.razorpay.com",
                "https://lumberjack.razorpay.com",
                "https://*.razorpay.com"));

        /** Lesson videos are streamed from the backend; blob: covers object URLs. */
        private List<String> mediaSrc = new ArrayList<>(List.of("'self'", "blob:", "data:"));

        /** Razorpay Checkout renders in an iframe; YouTube embeds are used for stories/lessons. */
        private List<String> frameSrc = new ArrayList<>(List.of(
                "'self'",
                "https://api.razorpay.com",
                "https://checkout.razorpay.com",
                "https://www.youtube.com",
                "https://www.youtube-nocookie.com"));

        private List<String> objectSrc = new ArrayList<>(List.of("'none'"));
        private List<String> baseUri = new ArrayList<>(List.of("'self'"));
        private List<String> formAction = new ArrayList<>(List.of("'self'"));
        private List<String> frameAncestors = new ArrayList<>(List.of("'self'"));
        private List<String> workerSrc = new ArrayList<>(List.of("'self'", "blob:"));

        /**
         * Extra origins appended to connect-src at runtime, e.g. the deployed frontend origin
         * or a CDN. Comma separated via app.security.headers.csp.extra-connect-src.
         */
        private List<String> extraConnectSrc = new ArrayList<>();

        /** Extra origins appended to script-src (kept empty by default). */
        private List<String> extraScriptSrc = new ArrayList<>();
    }
}

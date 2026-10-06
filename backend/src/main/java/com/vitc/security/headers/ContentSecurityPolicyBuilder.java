package com.vitc.security.headers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PART 2B-1/7 - builds the Content-Security-Policy header value once from
 * {@link SecurityHeadersProperties} and caches it (the policy is static per process).
 */
@Component
@RequiredArgsConstructor
public class ContentSecurityPolicyBuilder {

    private static final String UNSAFE_INLINE = "'unsafe-inline'";
    private static final String UNSAFE_EVAL = "'unsafe-eval'";

    private final SecurityHeadersProperties properties;

    private volatile String cached;

    public String policy() {
        String value = cached;
        if (value == null) {
            value = build();
            cached = value;
        }
        return value;
    }

    private String build() {
        SecurityHeadersProperties.Csp csp = properties.getCsp();

        List<String> scriptSrc = merge(csp.getScriptSrc(), csp.getExtraScriptSrc());
        if (properties.isAllowInlineScripts()) {
            scriptSrc.add(UNSAFE_INLINE);
        }
        if (properties.isAllowEval()) {
            scriptSrc.add(UNSAFE_EVAL);
        }

        List<String> styleSrc = merge(csp.getStyleSrc(), List.of());
        if (properties.isAllowInlineStyles()) {
            styleSrc.add(UNSAFE_INLINE);
        }

        StringBuilder sb = new StringBuilder();
        directive(sb, "default-src", csp.getDefaultSrc());
        directive(sb, "script-src", scriptSrc);
        directive(sb, "script-src-elem", scriptSrc);
        directive(sb, "style-src", styleSrc);
        directive(sb, "style-src-elem", styleSrc);
        directive(sb, "img-src", csp.getImgSrc());
        directive(sb, "font-src", csp.getFontSrc());
        directive(sb, "connect-src", merge(csp.getConnectSrc(), csp.getExtraConnectSrc()));
        directive(sb, "media-src", csp.getMediaSrc());
        directive(sb, "frame-src", csp.getFrameSrc());
        directive(sb, "worker-src", csp.getWorkerSrc());
        directive(sb, "object-src", csp.getObjectSrc());
        directive(sb, "base-uri", csp.getBaseUri());
        directive(sb, "form-action", csp.getFormAction());
        directive(sb, "frame-ancestors", csp.getFrameAncestors());
        return sb.toString().trim();
    }

    private static List<String> merge(List<String> primary, List<String> extra) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            primary.stream().filter(ContentSecurityPolicyBuilder::usable).forEach(merged::add);
        }
        if (extra != null) {
            extra.stream().filter(ContentSecurityPolicyBuilder::usable).forEach(merged::add);
        }
        return new ArrayList<>(merged);
    }

    private static boolean usable(String source) {
        return source != null && !source.isBlank();
    }

    private static void directive(StringBuilder sb, String name, List<String> sources) {
        List<String> values = merge(sources, List.of());
        if (values.isEmpty()) {
            return;
        }
        sb.append(name);
        for (String value : values) {
            sb.append(' ').append(value.trim());
        }
        sb.append("; ");
    }
}

package com.vitc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PART 10B - backend-enforced Main Admin boundary for the sensitive APIs that
 * do not live under the {@code /api/v1/admin/**} prefix: admin management
 * ({@code /api/v1/admins}), user/teacher accounts ({@code /api/v1/users}),
 * payments, orders and invoice listings.
 *
 * <p>Authorisation is decided here, on the server. Hiding a menu item or a
 * button in the Teacher Admin UI is never the control: a Teacher (or any other
 * authenticated non-admin caller) that hand-crafts a request to one of these
 * endpoints is rejected with <strong>403 Forbidden</strong>.</p>
 *
 * <p>A small allow-list keeps the genuinely public storefront and login flows
 * working exactly as before - they are matched by method + path, so for example
 * {@code POST /api/v1/enrollments} (a visitor enrolling) stays open while
 * {@code GET /api/v1/enrollments} (the admin enrollment list) requires a Main
 * Admin session.</p>
 */
@Component
@RequiredArgsConstructor
public class AdminOnlyApiInterceptor implements HandlerInterceptor {

    private final AdminAuthInterceptor adminAuthInterceptor;

    /** Public, credential-free endpoints that must keep working for visitors / login pages. */
    private static final List<String[]> PUBLIC_ALLOWLIST = List.of(
            // Main Admin authentication itself (issues / checks / clears the admin session).
            new String[] {"POST", "/api/v1/admins/login"},
            new String[] {"POST", "/api/v1/admins/session"},
            new String[] {"POST", "/api/v1/admins/logout"},
            new String[] {"POST", "/api/v1/admins/forgot-password"},
            new String[] {"POST", "/api/v1/admins/verify-otp"},
            new String[] {"POST", "/api/v1/admins/resend-otp"},
            new String[] {"POST", "/api/v1/admins/reset-password"},
            // Storefront: a visitor submits an enrollment request.
            new String[] {"POST", "/api/v1/enrollments"},
            // Storefront: invoice / order lookup by the customer's own order code or invoice number.
            new String[] {"GET", "/api/v1/invoices/order/**"},
            new String[] {"GET", "/api/v1/invoices/number/**"},
            new String[] {"GET", "/api/v1/courses"},
            new String[] {"GET", "/api/v1/courses/**"},
            new String[] {"GET", "/api/v1/gallery"},
            new String[] {"GET", "/api/v1/gallery/**"},
            // FIX - REVIEWER PHOTO UPLOAD: "/api/v1/files" is now routed through this interceptor
            // (see WebConfig) so the admin-only list/delete handlers are actually authenticated.
            // The generic upload endpoint itself has never carried @RequireRole - it is used by
            // visitor-facing forms (e.g. a student's own profile photo) as well as by the Main
            // Admin Reviewer Photo "Upload" button - so it stays public here exactly as before.
            // NOTE: "/api/v1/files/gallery" is deliberately NOT added here - that endpoint already
            // carries @RequireRole(MAIN_ADMIN) and was already correctly admin-gated (it was already
            // registered as an exact path above), so it must stay authenticated.
            new String[] {"POST", "/api/v1/files"},
            // Storefront: a visitor/student submits a review, browses approved/featured
            // reviews, or looks up a course's rating - only the Main-Admin-only endpoints
            // (list-all, get-by-id, update, approval, delete, by-email) stay behind auth.
            new String[] {"POST", "/api/v1/reviews"},
            new String[] {"GET", "/api/v1/reviews/page"},
            new String[] {"GET", "/api/v1/reviews/approved"},
            new String[] {"GET", "/api/v1/reviews/featured"},
            new String[] {"GET", "/api/v1/reviews/course/**"},
            // Storefront: a visitor applies for an internship - the admin list/detail/status
            // endpoints in InternshipApplicationController stay behind auth.
            new String[] {"POST", "/api/v1/internships"},
            // Storefront: a visitor applies for a job - the admin list/detail/status
            // endpoints in JobApplicationController stay behind auth.
            new String[] {"POST", "/api/v1/careers"},
            // Storefront: the public assignment catalogue (assignments.html /
            // assignment-details.html) reads these anonymously. Only GET is listed, so
            // POST / PUT / DELETE on the same paths still require a Main Admin session.
            new String[] {"GET", "/api/v1/assignments"},
            new String[] {"GET", "/api/v1/assignments/**"},
            // Storefront: contact messages, blog posts, testimonials, faqs, pricing
            new String[] {"POST", "/api/v1/contact-messages"},
            new String[] {"GET", "/api/v1/blog-posts/published"},
            new String[] {"GET", "/api/v1/blog-posts/slug/**"},
            new String[] {"GET", "/api/v1/testimonials/approved"},
            new String[] {"GET", "/api/v1/faqs"},
            new String[] {"GET", "/api/v1/faqs/**"},
            new String[] {"GET", "/api/v1/pricing"},
            new String[] {"GET", "/api/v1/pricing/**"});

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isPublic(request)) {
            return true;
        }
        // A Teacher or Student session token is authenticated but NOT authorised for the
        // Main Admin surface - answer 403 Forbidden without touching the admin store.
        if (hasHeader(request, "X-Teacher-Token")
                || hasHeader(request, "X-Teacher-Username")
                || hasHeader(request, "X-Student-Token")
                || hasHeader(request, "X-Student-Username")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Forbidden\"}");
            return false;
        }
        // Otherwise the caller must present a valid Main Admin session.
        return adminAuthInterceptor.preHandle(request, response, handler);
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        for (String[] entry : PUBLIC_ALLOWLIST) {
            if (!entry[0].equalsIgnoreCase(method)) {
                continue;
            }
            String pattern = entry[1];
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (path.startsWith(prefix) && path.length() > prefix.length()) {
                    return true;
                }
            } else if (pattern.equals(path) || pattern.equals(stripTrailingSlash(path))) {
                return true;
            }
        }
        return false;
    }

    private String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private boolean hasHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank();
    }
}

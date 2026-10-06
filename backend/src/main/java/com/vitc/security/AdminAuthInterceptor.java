package com.vitc.security;

import com.vitc.entity.Admin;
import com.vitc.repository.AdminRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards the admin-only payment settings endpoints using the session token the
 * existing admin login already issues (X-Admin-Username / X-Admin-Token, sent
 * by admin/assets/admin.js on every request). No second auth system is
 * introduced.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminRepository adminRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String username = request.getHeader("X-Admin-Username");
        String token = request.getHeader("X-Admin-Token");
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            // PART 10B - a Teacher / Student session is authenticated but not authorised for the
            // Main Admin surface, so it gets 403 Forbidden (not a 401 "please sign in").
            if (present(request, "X-Teacher-Token") || present(request, "X-Teacher-Username")
                    || present(request, "X-Student-Token") || present(request, "X-Student-Username")) {
                return deny(response, HttpStatus.FORBIDDEN, "Forbidden");
            }
            return deny(response, HttpStatus.UNAUTHORIZED, "Admin authentication required");
        }
        Admin admin = adminRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (admin == null
                || !Boolean.TRUE.equals(admin.getActive())
                || admin.getSessionToken() == null
                || !TokenSecurityUtil.matches(admin.getSessionToken(), token)
                || admin.getSessionExpiresAt() == null
                || admin.getSessionExpiresAt().isBefore(LocalDateTime.now())) {
            return deny(response, HttpStatus.FORBIDDEN, "Session expired, please sign in again");
        }
        // PART 11B-1 - publish the resolved caller for the centralized role check.
        CurrentUserContext.set(new CurrentUser(Role.MAIN_ADMIN, admin.getId(), admin.getUsername()));
        return true;
    }

    private boolean present(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank();
    }

    private boolean deny(HttpServletResponse response, HttpStatus status, String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
        return false;
    }
}

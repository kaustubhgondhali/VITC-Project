package com.vitc.security;

import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards /api/v1/teacher/** with the opaque session token issued by the
 * Teacher Admin login, mirroring the existing admin and student session
 * models. The authenticated teacher is resolved server-side and published as
 * the {@code teacherId} request attribute - a teacher id sent by the browser
 * is never trusted. Completely separate from {@link AdminAuthInterceptor}
 * and {@link StudentAuthInterceptor}: a Main Admin or Student session token
 * cannot be used here, and vice versa.
 */
@Component
@RequiredArgsConstructor
public class TeacherAuthInterceptor implements HandlerInterceptor {

    public static final String TEACHER_ID_ATTRIBUTE = "teacherId";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String username = request.getHeader("X-Teacher-Username");
        String token = request.getHeader("X-Teacher-Token");
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            // PART 8/8 - a caller that presents a VALID session for a different role
            // (Main Admin or Student) is authenticated but not authorised for the Teacher
            // area, so it must be rejected with 403 Forbidden, not 401.
            if (hasValidNonTeacherSession(request)) {
                return deny(response, HttpStatus.FORBIDDEN, "Forbidden");
            }
            return deny(response, HttpStatus.UNAUTHORIZED, "Teacher authentication required");
        }
        User user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        // Step 1 - authentication: a valid, unexpired session token for a real account.
        if (user == null
                || user.getSessionToken() == null
                || !TokenSecurityUtil.matches(user.getSessionToken(), token)
                || user.getSessionExpiresAt() == null
                || user.getSessionExpiresAt().isBefore(LocalDateTime.now())) {
            return deny(response, HttpStatus.UNAUTHORIZED, "Session expired, please sign in again");
        }
        // Step 2 - authorization (PART 10A): the authenticated account must be an ACTIVE TEACHER.
        // A valid Main Admin / Student / suspended account session is authenticated but NOT
        // authorised here, so it is rejected with 403 Forbidden, never allowed to continue.
        if (user.getRole() != UserRole.TEACHER || user.getStatus() != UserStatus.ACTIVE) {
            return deny(response, HttpStatus.FORBIDDEN, "Forbidden");
        }
        // Step 3 - course-level authorization happens per request in TeacherAuthorizationService.
        request.setAttribute(TEACHER_ID_ATTRIBUTE, user.getId());
        // PART 11B-1 - publish the resolved caller for the centralized role check.
        CurrentUserContext.set(new CurrentUser(Role.TEACHER, user.getId(), user.getUsername()));
        return true;
    }

    /**
     * True when the request carries a valid, unexpired Main Admin or Student session.
     * Used only to answer 401 (no identity) vs 403 (wrong role) correctly.
     */
    private boolean hasValidNonTeacherSession(HttpServletRequest request) {
        String studentId = request.getHeader("X-Student-Id");
        String studentToken = request.getHeader("X-Student-Token");
        if (studentId != null && !studentId.isBlank() && studentToken != null && !studentToken.isBlank()) {
            User student = userRepository.findByStudentLoginIdIgnoreCase(studentId.trim()).orElse(null);
            if (isLiveSession(student, studentToken)) {
                return true;
            }
        }
        String adminUsername = request.getHeader("X-Admin-Username");
        String adminToken = request.getHeader("X-Admin-Token");
        if (adminUsername != null && !adminUsername.isBlank() && adminToken != null && !adminToken.isBlank()) {
            User admin = userRepository.findByUsernameIgnoreCase(adminUsername.trim()).orElse(null);
            if (isLiveSession(admin, adminToken)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLiveSession(User user, String token) {
        return user != null
                && user.getSessionToken() != null
                && TokenSecurityUtil.matches(user.getSessionToken(), token)
                && user.getSessionExpiresAt() != null
                && user.getSessionExpiresAt().isAfter(LocalDateTime.now());
    }

    private boolean deny(HttpServletResponse response, HttpStatus status, String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
        return false;
    }
}

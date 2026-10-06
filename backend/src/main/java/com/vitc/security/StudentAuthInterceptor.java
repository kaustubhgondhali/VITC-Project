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
 * Guards /api/v1/student/** with the opaque session token issued by the student
 * login, mirroring the existing admin session model. The authenticated student
 * is resolved server-side and published as the {@code studentId} request
 * attribute - a student id sent by the browser is never trusted.
 */
@Component
@RequiredArgsConstructor
public class StudentAuthInterceptor implements HandlerInterceptor {

    public static final String STUDENT_ID_ATTRIBUTE = "studentId";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String loginId = request.getHeader("X-Student-Id");
        String token = request.getHeader("X-Student-Token");
        if (loginId == null || loginId.isBlank() || token == null || token.isBlank()) {
            // PART 13 - mirrors TeacherAuthInterceptor/AdminAuthInterceptor: a caller that
            // presents a VALID session for a different role (Main Admin or Teacher) is
            // authenticated but not authorised for the Student area, so it must be
            // rejected with 403 Forbidden, not a 401 "please sign in" that implies the
            // caller has no session at all.
            if (hasValidNonStudentSession(request)) {
                return deny(response, HttpStatus.FORBIDDEN, "Forbidden");
            }
            return deny(response, HttpStatus.UNAUTHORIZED, "Student authentication required");
        }
        User user = userRepository.findByStudentLoginIdIgnoreCase(loginId.trim()).orElse(null);
        // Step 1 - authentication: a valid, unexpired session token for a real account.
        if (user == null
                || user.getSessionToken() == null
                || !TokenSecurityUtil.matches(user.getSessionToken(), token)
                || user.getSessionExpiresAt() == null
                || user.getSessionExpiresAt().isBefore(LocalDateTime.now())) {
            return deny(response, HttpStatus.UNAUTHORIZED, "Session expired, please sign in again");
        }
        // Step 2 - authorization (PART 11B-2): the authenticated account must be an ACTIVE STUDENT.
        // A valid Main Admin / Teacher / suspended account session is authenticated but NOT
        // authorised here, so it is rejected with 403 Forbidden, never allowed to continue.
        if (user.getRole() != UserRole.STUDENT) {
            return deny(response, HttpStatus.FORBIDDEN, "Forbidden");
        }
        // PART 8/8 - a blocked/suspended student is a revoked session, not a wrong role:
        // the portal must sign them out, so it answers 401 Unauthorized.
        if (user.getStatus() != UserStatus.ACTIVE) {
            return deny(response, HttpStatus.UNAUTHORIZED, "Your account is no longer active, please sign in again");
        }
        request.setAttribute(STUDENT_ID_ATTRIBUTE, user.getId());
        // PART 11B-1 - publish the resolved caller for the centralized role check.
        CurrentUserContext.set(new CurrentUser(Role.STUDENT, user.getId(), user.getStudentLoginId()));
        return true;
    }

    /**
     * True when the request carries a valid, unexpired Main Admin or Teacher session.
     * Used only to answer 401 (no identity) vs 403 (wrong role) correctly - PART 13,
     * matching {@code TeacherAuthInterceptor.hasValidNonTeacherSession}.
     */
    private boolean hasValidNonStudentSession(HttpServletRequest request) {
        String teacherUsername = request.getHeader("X-Teacher-Username");
        String teacherToken = request.getHeader("X-Teacher-Token");
        if (teacherUsername != null && !teacherUsername.isBlank() && teacherToken != null && !teacherToken.isBlank()) {
            User teacher = userRepository.findByUsernameIgnoreCase(teacherUsername.trim()).orElse(null);
            if (isLiveSession(teacher, teacherToken)) {
                return true;
            }
        }
        String adminUsername = request.getHeader("X-Admin-Username");
        String adminToken = request.getHeader("X-Admin-Token");
        return adminUsername != null && !adminUsername.isBlank() && adminToken != null && !adminToken.isBlank();
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

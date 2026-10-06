package com.vitc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PART 11B-1 - the centralized role check.
 *
 * <p>Registered globally (see {@code WebConfig}) so it runs, in order, after
 * whichever existing session interceptor authenticated the request
 * ({@code AdminAuthInterceptor}, {@code AdminOnlyApiInterceptor},
 * {@code StudentAuthInterceptor} or {@code TeacherAuthInterceptor}). Those
 * interceptors are still the ones that verify the session token and populate
 * {@link CurrentUserContext}; this interceptor only asks one question -
 * "is the resolved caller's role allowed on this handler?" - by reading
 * {@link RequireRole} off the target controller method, falling back to the
 * controller class.
 *
 * <p>No {@code @RequireRole} on a handler means this interceptor does not
 * touch it, so every endpoint keeps behaving exactly as it does today unless
 * it is explicitly opted in. Backend authorization is enforced here
 * regardless of what any frontend menu, button, or route guard shows -
 * a disallowed role always gets 403 Forbidden, never the response body.</p>
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole methodLevel = handlerMethod.getMethodAnnotation(RequireRole.class);
        RequireRole classLevel = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        RequireRole effective = methodLevel != null ? methodLevel : classLevel;
        if (effective == null) {
            // Not opted into the centralized annotation check; the path-based
            // session interceptors already guard this endpoint (or it is public).
            return true;
        }

        Set<Role> allowed = Set.of(effective.value());
        CurrentUser caller = CurrentUserContext.get();
        if (caller == null || !allowed.contains(caller.role())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Forbidden\"}");
            return false;
        }
        return true;
    }
}

package com.vitc.security;

/**
 * PART 11B-1 - describes who is making the current request, as resolved
 * server-side by whichever auth interceptor authenticated them
 * ({@code AdminAuthInterceptor}, {@code AdminOnlyApiInterceptor},
 * {@code TeacherAuthInterceptor} or {@code StudentAuthInterceptor}).
 *
 * <p>Never built from client-supplied ids - {@code id} and {@code identifier}
 * are always looked up from the database row matched by the caller's session
 * token, exactly as the existing interceptors already do for
 * {@code teacherId} / {@code studentId} request attributes.</p>
 *
 * @param role       the unified {@link Role} the caller was authenticated as
 * @param id         the database id of the Admin/User row backing this session
 * @param identifier the login identifier (admin username, teacher username, or
 *                   student login id) - useful for audit logging
 */
public record CurrentUser(Role role, Long id, String identifier) {
}

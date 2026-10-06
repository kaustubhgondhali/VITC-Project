package com.vitc.security;

/**
 * PART 11B-1 - a single, centralized place that holds who the current request
 * is authenticated as. Populated by the existing auth interceptors once they
 * have verified a session token against the database, and read by
 * {@link RoleAuthorizationInterceptor} (and, optionally, by controllers or
 * services that want to know the caller without re-deriving it from headers).
 *
 * <p>Backed by a {@link ThreadLocal} because Spring MVC handles one request
 * per worker thread. {@link CurrentUserContextFilter} guarantees the value is
 * cleared at the end of every request - including requests an interceptor
 * rejected - so nothing can leak onto a pooled thread that later serves a
 * different, unrelated request.</p>
 */
public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /** Returns the authenticated caller for this request, or {@code null} if none was resolved. */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

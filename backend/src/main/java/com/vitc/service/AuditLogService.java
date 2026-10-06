package com.vitc.service;

/**
 * PART 2C-1/7 - the single, centralized entry point for recording an audit
 * event. Every controller/service that needs to record one calls this
 * interface instead of writing to {@code audit_logs} directly, so identity
 * resolution (who/role/ip/user-agent) and secret-sanitization happen exactly
 * once, in one place.
 *
 * <p>Who is acting is <strong>always</strong> derived server-side from
 * {@link com.vitc.security.CurrentUserContext} and the current HTTP request -
 * never from a caller-supplied user id, role, or IP address. When no
 * authenticated user is present on the current request (e.g. a failed login
 * attempt, or a security event before authentication completes), the event
 * is recorded as anonymous rather than rejected.</p>
 */
public interface AuditLogService {

    /**
     * Records an audit event tied to a specific entity.
     *
     * @param action     short machine-readable action code, e.g. {@code "STUDENT_CREATED"}
     * @param entityType type of entity acted on, e.g. {@code "Student"} (nullable)
     * @param entityId   id of the entity acted on (nullable)
     * @param description human-readable summary; sanitized before persistence -
     *                    never pass a password, token, or other secret here
     */
    void log(String action, String entityType, String entityId, String description);

    /**
     * Records an audit event that isn't about a specific entity, e.g. a login
     * attempt or a settings change with no single owning record.
     */
    void log(String action, String description);
}

package com.vitc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PART 2C-1/7 - a single, append-only record of a security-relevant or
 * administrative action taken anywhere in the backend (Main Admin, Teacher
 * Admin, or Student Portal).
 *
 * <p>Rows are written exclusively through {@link com.vitc.service.AuditLogService}
 * so every caller goes through the same identity-resolution and
 * secret-sanitization logic - never assembled ad-hoc inside a controller.</p>
 *
 * <p>Never stores passwords, tokens, API keys, or any other secret. See
 * {@code AuditLogServiceImpl#sanitize} for the redaction applied to
 * {@link #description} before a row is ever persisted.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "audit_logs",
        indexes = {
            @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
            @Index(name = "idx_audit_logs_action", columnList = "action"),
            @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id"),
            @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
        })
public class AuditLog extends BaseEntity {

    /** Database id of the acting Admin/User row. Null for anonymous/unauthenticated events. */
    @Column(name = "user_id")
    private Long userId;

    /** Login identifier of the acting user (admin username, teacher username, student login id). */
    @Column(name = "actor_identifier", length = 60)
    private String actorIdentifier;

    /** Unified {@link com.vitc.security.Role} name as a string. Null for anonymous events. */
    @Column(name = "role", length = 20)
    private String role;

    /** Short machine-readable action code, e.g. "LOGIN_SUCCESS", "PASSWORD_RESET_COMPLETED". */
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    /** Type of entity acted on, e.g. "Student", "Course", "Enrollment". Null when not applicable. */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    /** Id of the entity acted on, stored as text since entity id types vary. Null when not applicable. */
    @Column(name = "entity_id", length = 60)
    private String entityId;

    /** Human-readable, already-sanitized summary of the event. Never contains a secret or token. */
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}

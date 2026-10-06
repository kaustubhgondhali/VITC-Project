package com.vitc.entity;

import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    /** BCrypt hash. Never exposed through the API. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 120)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserRole role = UserRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /* ------------------------------------------------------------------
       Student portal fields (added incrementally - all nullable so every
       existing row and every existing API keeps working unchanged).
       ------------------------------------------------------------------ */

    /** Public login handle, e.g. VITCSTU10001. Never the raw database id. */
    @Column(name = "student_login_id", unique = true, length = 30)
    private String studentLoginId;

    /**
     * General-purpose login username for non-student roles that authenticate
     * against this same table (currently: {@link UserRole#TEACHER}). Kept
     * separate from {@link #studentLoginId} because that field is reserved
     * for the sequential VITCSTU#### format and is parsed as such elsewhere.
     */
    @Column(name = "username", unique = true, length = 60)
    private String username;

    /** True while the account is still using a temporary/first-issued password. */
    @Column(name = "must_change_password")
    @Builder.Default
    private Boolean mustChangePassword = false;

    /** Opaque session token; shared session model reused across student/teacher roles. */
    @Column(name = "student_session_token", length = 100)
    private String sessionToken;

    @Column(name = "student_session_expires_at")
    private LocalDateTime sessionExpiresAt;

    @Column(name = "student_last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Student profile picture (Part 6). Stores the public URL returned by the
     * existing {@code FileStorageService} upload endpoint - no new upload
     * pipeline, no external service. Nullable: most rows never set it.
     */
    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    /**
     * Part 4 - Student "forgot password" one-time reset code. Same pattern as
     * {@link Admin#getResetToken()}, reused rather than duplicated: a random,
     * single-use, time-limited code stored against the account and cleared
     * the moment it is consumed (or replaced by a fresh "forgot password"
     * request). Never returned by any API - it is only ever emailed to the
     * account's own registered address.
     */
    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;
}

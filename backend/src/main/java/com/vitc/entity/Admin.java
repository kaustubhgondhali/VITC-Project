package com.vitc.entity;

import com.vitc.entity.enums.AdminRole;
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
@Table(name = "admins")
public class Admin extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** BCrypt hash. Never exposed through the API. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AdminRole role = AdminRole.ADMIN;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Opaque session token handed out on a successful login; cleared on logout. */
    @Column(name = "session_token", length = 100)
    private String sessionToken;

    @Column(name = "session_expires_at")
    private LocalDateTime sessionExpiresAt;

    /** One-time password-reset token. */
    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;
}

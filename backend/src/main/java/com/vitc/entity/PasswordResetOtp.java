package com.vitc.entity;

import com.vitc.entity.enums.DeliveryChannel;
import com.vitc.entity.enums.RecoveryPortal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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
@Table(name = "password_reset_otps", indexes = {
        @Index(name = "idx_otp_recovery_token", columnList = "recovery_token", unique = true),
        @Index(name = "idx_otp_reset_token", columnList = "reset_token"),
        @Index(name = "idx_otp_account", columnList = "account_id, account_role")
})
public class PasswordResetOtp extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 30)
    private RecoveryPortal accountRole;

    @Column(name = "target_contact", nullable = false, length = 150)
    private String targetContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false, length = 20)
    @Builder.Default
    private DeliveryChannel deliveryChannel = DeliveryChannel.EMAIL;

    /** Hashed 6-digit OTP. Never stored in plaintext. */
    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    /** Unique recovery session token returned to frontend to identify the OTP session. */
    @Column(name = "recovery_token", nullable = false, unique = true, length = 64)
    private String recoveryToken;

    /** Short-lived authorization token generated only after successful OTP verification. */
    @Column(name = "reset_token", length = 64)
    private String resetToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;
}


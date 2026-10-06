package com.vitc.entity;

import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EmailType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Delivery record for a transactional email. Never stores passwords or any
 * other secret - only who was mailed, which template, and the outcome.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "email_delivery_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_dedupe_key", columnNames = "dedupe_key"))
public class EmailDeliveryLog extends BaseEntity {

    /** Idempotency key, e.g. STUDENT_CREDENTIALS:42 or COURSE_ADDED:ORD-1001. */
    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 40)
    private EmailType emailType;

    @Column(name = "recipient_email", nullable = false, length = 160)
    private String recipientEmail;

    @Column(name = "student_login_id", length = 40)
    private String studentLoginId;

    @Column(name = "student_user_id")
    private Long studentUserId;

    @Column(name = "order_code", length = 40)
    private String orderCode;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;

    /** Short, non-sensitive failure summary. Never contains credentials. */
    @Column(name = "failure_reason", length = 400)
    private String failureReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
}

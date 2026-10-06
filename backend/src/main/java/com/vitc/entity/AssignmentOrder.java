package com.vitc.entity;

import jakarta.persistence.*;
import com.vitc.entity.enums.OrderStatus;
import java.math.BigDecimal;
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
@Table(name = "assignment_orders")
public class AssignmentOrder extends BaseEntity {

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(name = "student_name", nullable = false, length = 120)
    private String studentName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String college;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(length = 1000)
    private String requirements;

    /*
     * Delivery of the purchased project package. All nullable: an order has none of these until a
     * Main Admin uploads its files, and existing rows must keep loading unchanged.
     */

    /** Generated file name inside app.assignment.delivery-dir - never the uploader's file name. */
    @Column(name = "delivery_file_path", length = 255)
    private String deliveryFilePath;

    /** Sanitised original file name, used only as the download's suggested name. */
    @Column(name = "delivery_file_name", length = 160)
    private String deliveryFileName;

    @Column(name = "delivery_content_type", length = 120)
    private String deliveryContentType;

    @Column(name = "delivery_size_bytes")
    private Long deliverySizeBytes;

    /** Optional message from the team, shown in the delivery email and on the download page. */
    @Column(name = "delivery_note", length = 1000)
    private String deliveryNote;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** SHA-256 of the emailed download token; the token itself is never stored. */
    @Column(name = "download_token_hash", length = 64)
    private String downloadTokenHash;

    @Column(name = "download_expires_at")
    private LocalDateTime downloadExpiresAt;

    @Column(name = "download_count")
    private Integer downloadCount;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

}

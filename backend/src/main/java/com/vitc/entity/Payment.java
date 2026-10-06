package com.vitc.entity;

import jakarta.persistence.*;
import com.vitc.entity.enums.PaymentMethod;
import com.vitc.entity.enums.PaymentStatus;
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
@Table(name = "payments")
public class Payment extends BaseEntity {

    /** Legacy reference (COURSE / ASSIGNMENT) kept for backwards compatibility. */
    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /** Checkout order this payment settles (null for legacy direct payments). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PaymentOrder order;

    @Column(name = "transaction_id", unique = true, length = 80)
    private String transactionId;

    @Column(name = "payer_name", nullable = false, length = 120)
    private String payerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /** Free-form method detail: UPI id, masked card, wallet or bank name. */
    @Column(name = "method_detail", length = 120)
    private String methodDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.INITIATED;

    /** Gateway identity so a future Razorpay switch is traceable per row. */
    @Column(length = 30)
    @Builder.Default
    private String provider = "MOCK";

    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 120)
    private String providerPaymentId;

    @Column(name = "provider_signature", length = 255)
    private String providerSignature;

    @Column(name = "gateway_response", length = 2000)
    private String gatewayResponse;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}

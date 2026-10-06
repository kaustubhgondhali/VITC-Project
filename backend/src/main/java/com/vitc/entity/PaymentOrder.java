package com.vitc.entity;

import com.vitc.entity.enums.PaymentOrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A checkout order. Created before any money movement so that every payment
 * attempt (mock today, Razorpay tomorrow) has a stable order to settle against.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends BaseEntity {

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    /** COURSE or ASSIGNMENT. */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @Column(name = "item_ref_id")
    private Long itemRefId;

    @Column(name = "item_title", nullable = false, length = 200)
    private String itemTitle;

    @Column(name = "item_meta", length = 250)
    private String itemMeta;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 120)
    private String city;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("18.00");

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.CREATED;

    @Column(length = 1000)
    private String notes;
}

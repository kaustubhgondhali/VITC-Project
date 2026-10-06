package com.vitc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "pricing_plans")
public class PricingPlan extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String tagline;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(length = 40)
    private String currency;

    @Column(name = "billing_period", length = 40)
    private String billingPeriod;

    /** Newline separated feature list. */
    @Column(length = 4000)
    private String features;

    @Column(length = 60)
    private String category;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean highlighted = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}

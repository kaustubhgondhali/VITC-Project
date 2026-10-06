package com.vitc.entity;

import jakarta.persistence.*;
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
@Table(name = "assignments")
public class Assignment extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 120)
    private String tech;

    @Column(length = 40)
    private String difficulty;

    @Column(name = "delivery_days", length = 40)
    private String deliveryDays;

    @Column(length = 60)
    private String category;

    @Column(length = 20)
    private String icon;

    @Column(name = "features", length = 2000)
    private String features;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /*
     * Ready-made project package (Admin -> Assignment Upload). When set, every buyer of this
     * assignment is sent a private download link as soon as their payment is verified. Stored
     * under app.assignment.delivery-dir and never exposed by the public catalogue API.
     */

    @Column(name = "project_file_path", length = 255)
    private String projectFilePath;

    @Column(name = "project_file_name", length = 160)
    private String projectFileName;

    @Column(name = "project_content_type", length = 120)
    private String projectContentType;

    @Column(name = "project_size_bytes")
    private Long projectSizeBytes;

    @Column(name = "project_uploaded_at")
    private LocalDateTime projectUploadedAt;

}

package com.vitc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "reviews")
public class Review extends BaseEntity {

    @Column(name = "reviewer_name", nullable = false, length = 120)
    private String reviewerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "course_code", length = 60)
    private String courseCode;

    @Column(name = "course_title", length = 150)
    private String courseTitle;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 2000)
    private String comment;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean approved = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;
}

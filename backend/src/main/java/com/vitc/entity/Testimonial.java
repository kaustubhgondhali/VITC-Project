package com.vitc.entity;

import jakarta.persistence.*;
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
@Table(name = "testimonials")
public class Testimonial extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String role;

    @Column(name = "photo_url", length = 400)
    private String photoUrl;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 1500)
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private Boolean approved = false;

}

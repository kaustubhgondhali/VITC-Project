package com.vitc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A chapter of a course. Course -> CourseModule -> CourseLesson is the only
 * course-content structure in the project; nothing here duplicates the
 * existing Course entity, it only groups its lessons.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_modules")
public class CourseModule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}

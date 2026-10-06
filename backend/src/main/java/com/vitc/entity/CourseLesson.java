package com.vitc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single video lesson inside a {@link CourseModule}.
 *
 * <p>{@code videoUrl} is protected content: it is only ever serialised by the
 * student lesson endpoint, after the backend has verified an active enrolment
 * for the owning course.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_lessons")
public class CourseLesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Never exposed in list responses. */
    @Column(name = "video_url", length = 600)
    private String videoUrl;

    @Column(name = "audio_url", length = 600)
    private String audioUrl;

    /** Human readable length, e.g. "12:40". */
    @Column(length = 40)
    private String duration;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}

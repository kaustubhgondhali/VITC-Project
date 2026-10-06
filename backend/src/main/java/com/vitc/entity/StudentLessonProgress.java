package com.vitc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-student, per-lesson watch progress. One row per (student, lesson). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "student_lesson_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_lesson", columnNames = {"student_id", "lesson_id"}))
public class StudentLessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private CourseLesson lesson;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "progress_percentage", nullable = false)
    @Builder.Default
    private Integer progressPercentage = 0;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

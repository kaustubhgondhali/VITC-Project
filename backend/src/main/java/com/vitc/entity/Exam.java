package com.vitc.entity;

import com.vitc.entity.enums.ExamStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "exams", indexes = {@Index(name = "idx_exam_enrollment", columnList = "enrollment_id")})
public class Exam extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ExamStatus status = ExamStatus.ELIGIBLE;

    private LocalDate examDate;
    private LocalDate appliedDate;
    @Column(nullable = false)
    private LocalDate completionDate;
    private LocalTime examTime;
    @Column(length = 40) private String mode;
    @Column(length = 255) private String locationOrLink;
    @Column(length = 1000) private String notes;
}

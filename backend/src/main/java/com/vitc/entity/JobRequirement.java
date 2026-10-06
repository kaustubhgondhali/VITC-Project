package com.vitc.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "job_requirements", indexes = {@Index(name = "idx_job_public", columnList = "published")})
public class JobRequirement extends BaseEntity {
    @Column(nullable = false, length = 150) private String jobTitle;
    @Column(nullable = false, length = 150) private String companyName;
    @Column(nullable = false, length = 3000) private String description;
    @Column(nullable = false, length = 1000) private String requiredSkills;
    @Column(length = 100) private String experience;
    @Column(length = 150) private String qualification;
    @Column(length = 150) private String location;
    @Column(length = 80) private String employmentType;
    @Column(length = 100) private String salaryCtc;
    private Integer openings;
    private java.time.LocalDate applicationDeadline;
    @Column(length = 500) private String applicationMethod;
    @Column(nullable = false) @Builder.Default private Boolean published = false;
}

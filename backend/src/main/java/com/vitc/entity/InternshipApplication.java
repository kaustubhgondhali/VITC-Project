package com.vitc.entity;

import jakarta.persistence.*;
import com.vitc.entity.enums.ApplicationStatus;
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
@Table(name = "internship_applications")
public class InternshipApplication extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String college;

    @Column(nullable = false, length = 120)
    private String domain;

    @Column(length = 60)
    private String duration;

    @Column(name = "resume_url", length = 400)
    private String resumeUrl;

    @Column(length = 1500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

}

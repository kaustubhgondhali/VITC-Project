package com.vitc.entity;

import com.vitc.entity.enums.EnquiryStatus;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "employer_enquiries", indexes = {@Index(name = "idx_enquiry_status", columnList = "status")})
public class EmployerEnquiry extends BaseEntity {
    @Column(nullable = false, length = 150) private String companyName;
    @Column(nullable = false, length = 120) private String contactPerson;
    @Column(nullable = false, length = 150) private String email;
    @Column(nullable = false, length = 20) private String phone;
    @Column(nullable = false, length = 150) private String jobTitle;
    private Integer openings;
    @Column(length = 1000) private String requiredSkills;
    @Column(length = 100) private String experienceRequired;
    @Column(length = 150) private String location;
    @Column(nullable = false, length = 3000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private EnquiryStatus status = EnquiryStatus.NEW;
}

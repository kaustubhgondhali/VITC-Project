package com.vitc.entity;

import jakarta.persistence.*;
import com.vitc.entity.enums.ApplicationStatus;
import java.time.LocalDate;
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
@Table(name = "job_applications")
public class JobApplication extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 120)
    private String position;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "resume_url", length = 400)
    private String resumeUrl;

    @Column(length = 1500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    /*
     * Job Portal applications (career.html -> job-apply.html). Everything below is nullable so the
     * older general /api/v1/careers applications keep loading unchanged.
     */

    /**
     * The job requirement applied for. A plain id, not a JPA relationship, so deleting a job in
     * Admin -> Job Portal never fails because of its applications; {@link #position} and
     * {@link #companyName} keep a snapshot of what the applicant actually applied to.
     */
    @Column(name = "job_requirement_id")
    private Long jobRequirementId;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "current_city", length = 120)
    private String currentCity;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String gender;

    @Column(name = "highest_qualification", length = 150)
    private String highestQualification;

    @Column(length = 200)
    private String institution;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "academic_score", length = 30)
    private String academicScore;

    @Column(name = "current_company", length = 150)
    private String currentCompany;

    @Column(name = "current_ctc", length = 60)
    private String currentCtc;

    @Column(name = "expected_ctc", length = 60)
    private String expectedCtc;

    @Column(name = "notice_period", length = 60)
    private String noticePeriod;

    @Column(length = 1000)
    private String skills;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 300)
    private String portfolioUrl;

    /** Generated file name inside app.job.resume-dir - private, streamed to admins only. */
    @Column(name = "resume_file_path", length = 255)
    private String resumeFilePath;

    /** Sanitised original file name, used only as the download's suggested name. */
    @Column(name = "resume_file_name", length = 160)
    private String resumeFileName;

    @Column(name = "resume_content_type", length = 120)
    private String resumeContentType;

    @Column(name = "resume_size_bytes")
    private Long resumeSizeBytes;

    /** Recruiter-only notes from Admin -> Job Applications. Never shown to the applicant. */
    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

}

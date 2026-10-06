package com.vitc.dto.response;

import com.vitc.entity.enums.ApplicationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Admin -> Job Applications: one applicant's full profile and which job/company it was for. */
public record AdminJobApplicationResponse(
        Long id,
        String reference,
        Long jobRequirementId,
        String jobTitle,
        /** Null for older general career applications that were not made for a listed job. */
        String companyName,
        String fullName,
        String email,
        String phone,
        String currentCity,
        LocalDate dateOfBirth,
        String gender,
        String highestQualification,
        String institution,
        Integer graduationYear,
        String academicScore,
        Integer experienceYears,
        String currentCompany,
        String currentCtc,
        String expectedCtc,
        String noticePeriod,
        String skills,
        String linkedinUrl,
        String portfolioUrl,
        String coverLetter,
        /** True when a privately stored resume can be downloaded from /resume. */
        boolean hasResume,
        String resumeFileName,
        Long resumeSizeBytes,
        /** Older applications may only carry a link to a resume uploaded elsewhere. */
        String resumeUrl,
        ApplicationStatus status,
        String adminNotes,
        LocalDateTime submittedAt) {
}

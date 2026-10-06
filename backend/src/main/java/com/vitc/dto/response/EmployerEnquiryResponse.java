package com.vitc.dto.response;

import com.vitc.entity.EmployerEnquiry;
import com.vitc.entity.enums.EnquiryStatus;
import java.time.LocalDateTime;

public record EmployerEnquiryResponse(Long id, String companyName, String contactPerson, String email,
        String phone, String jobTitle, Integer openings, String requiredSkills, String experienceRequired,
        String location, String message, EnquiryStatus status, LocalDateTime submittedAt) {
    public static EmployerEnquiryResponse of(EmployerEnquiry e) {
        return new EmployerEnquiryResponse(e.getId(), e.getCompanyName(), e.getContactPerson(), e.getEmail(), e.getPhone(),
                e.getJobTitle(), e.getOpenings(), e.getRequiredSkills(), e.getExperienceRequired(), e.getLocation(),
                e.getMessage(), e.getStatus(), e.getCreatedAt());
    }
}

package com.vitc.dto.response;

import com.vitc.entity.JobRequirement;
import java.time.LocalDate;

public record JobRequirementResponse(Long id, String jobTitle, String companyName, String description,
        String requiredSkills, String experience, String qualification, String location,
        String employmentType, String salaryCtc, Integer openings, LocalDate applicationDeadline,
        String applicationMethod, Boolean published) {
    public static JobRequirementResponse of(JobRequirement j) {
        return new JobRequirementResponse(j.getId(), j.getJobTitle(), j.getCompanyName(), j.getDescription(),
                j.getRequiredSkills(), j.getExperience(), j.getQualification(), j.getLocation(), j.getEmploymentType(),
                j.getSalaryCtc(), j.getOpenings(), j.getApplicationDeadline(), j.getApplicationMethod(), j.getPublished());
    }
}

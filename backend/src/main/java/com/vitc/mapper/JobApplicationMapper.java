package com.vitc.mapper;

import com.vitc.dto.request.JobApplicationRequest;
import com.vitc.dto.response.JobApplicationResponse;
import com.vitc.entity.JobApplication;

public final class JobApplicationMapper {

    private JobApplicationMapper() {
    }

    public static JobApplication toEntity(JobApplicationRequest request) {
        JobApplication entity = new JobApplication();
        apply(entity, request);
        return entity;
    }

    public static void apply(JobApplication entity, JobApplicationRequest request) {
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setPosition(request.position());
        entity.setExperienceYears(request.experienceYears());
        entity.setResumeUrl(request.resumeUrl());
        entity.setMessage(request.message());
    }

    public static JobApplicationResponse toResponse(JobApplication entity) {
        return new JobApplicationResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPosition(),
                entity.getExperienceYears(),
                entity.getResumeUrl(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}

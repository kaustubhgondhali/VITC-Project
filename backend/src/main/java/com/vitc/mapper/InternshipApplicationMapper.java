package com.vitc.mapper;

import com.vitc.dto.request.InternshipApplicationRequest;
import com.vitc.dto.response.InternshipApplicationResponse;
import com.vitc.entity.InternshipApplication;

public final class InternshipApplicationMapper {

    private InternshipApplicationMapper() {
    }

    public static InternshipApplication toEntity(InternshipApplicationRequest request) {
        InternshipApplication entity = new InternshipApplication();
        apply(entity, request);
        return entity;
    }

    public static void apply(InternshipApplication entity, InternshipApplicationRequest request) {
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setCollege(request.college());
        entity.setDomain(request.domain());
        entity.setDuration(request.duration());
        entity.setResumeUrl(request.resumeUrl());
        entity.setMessage(request.message());
    }

    public static InternshipApplicationResponse toResponse(InternshipApplication entity) {
        return new InternshipApplicationResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getCollege(),
                entity.getDomain(),
                entity.getDuration(),
                entity.getResumeUrl(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}

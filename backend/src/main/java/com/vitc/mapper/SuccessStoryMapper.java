package com.vitc.mapper;

import com.vitc.dto.request.SuccessStoryRequest;
import com.vitc.dto.response.SuccessStoryResponse;
import com.vitc.entity.SuccessStory;

public final class SuccessStoryMapper {

    private SuccessStoryMapper() {
    }

    public static SuccessStory toEntity(SuccessStoryRequest request) {
        SuccessStory entity = new SuccessStory();
        apply(entity, request);
        return entity;
    }

    public static void apply(SuccessStory entity, SuccessStoryRequest request) {
        entity.setName(request.name());
        entity.setCategory(request.category() == null ? null : request.category().toLowerCase());
        entity.setCourseProgram(request.courseProgram());
        entity.setCourseId(request.courseId());
        entity.setDesignation(request.designation());
        entity.setDescription(request.description());
        entity.setVideoUrl(request.videoUrl());
        entity.setThumbnailUrl(request.thumbnailUrl());
        entity.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    /** {@code courseTitle} is resolved by the service (needs a {@code CourseRepository} lookup); pass null if not applicable. */
    public static SuccessStoryResponse toResponse(SuccessStory entity, String courseTitle) {
        return new SuccessStoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.getCourseProgram(),
                entity.getCourseId(),
                courseTitle,
                entity.getDesignation(),
                entity.getDescription(),
                entity.getVideoUrl(),
                entity.getThumbnailUrl(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getDisplayOrder(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public static SuccessStoryResponse toResponse(SuccessStory entity) {
        return toResponse(entity, null);
    }
}

package com.vitc.mapper;

import com.vitc.dto.request.CourseRequest;
import com.vitc.dto.response.CourseResponse;
import com.vitc.entity.Course;

public final class CourseMapper {

    private CourseMapper() {
    }

    public static Course toEntity(CourseRequest request) {
        Course entity = new Course();
        apply(entity, request);
        return entity;
    }

    public static void apply(Course entity, CourseRequest request) {
        entity.setCode(request.code());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
        entity.setMeta(request.meta());
        entity.setLevel(request.level());
        entity.setCategory(request.category());
        entity.setDurationMonths(request.durationMonths());
        entity.setIcon(request.icon());
        entity.setActive(request.active() == null || request.active());
    }

    public static CourseResponse toResponse(Course entity) {
        return new CourseResponse(
                entity.getId(),
                entity.getCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getMeta(),
                entity.getLevel(),
                entity.getCategory(),
                entity.getDurationMonths(),
                entity.getIcon(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

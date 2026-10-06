package com.vitc.mapper;

import com.vitc.dto.request.AssignmentRequest;
import com.vitc.dto.response.AssignmentResponse;
import com.vitc.entity.Assignment;

public final class AssignmentMapper {

    private AssignmentMapper() {
    }

    public static Assignment toEntity(AssignmentRequest request) {
        Assignment entity = new Assignment();
        apply(entity, request);
        return entity;
    }

    public static void apply(Assignment entity, AssignmentRequest request) {
        entity.setCode(request.code());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPrice(request.price());
        entity.setTech(request.tech());
        entity.setDifficulty(request.difficulty());
        entity.setDeliveryDays(request.deliveryDays());
        entity.setCategory(request.category());
        entity.setIcon(request.icon());
        entity.setFeatures(request.features());
        entity.setActive(request.active() == null || request.active());
    }

    public static AssignmentResponse toResponse(Assignment entity) {
        return new AssignmentResponse(
                entity.getId(),
                entity.getCode(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getTech(),
                entity.getDifficulty(),
                entity.getDeliveryDays(),
                entity.getCategory(),
                entity.getIcon(),
                entity.getFeatures(),
                entity.getActive(),
                entity.getCreatedAt());
    }
}

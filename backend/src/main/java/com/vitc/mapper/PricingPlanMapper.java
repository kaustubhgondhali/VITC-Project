package com.vitc.mapper;

import com.vitc.dto.request.PricingPlanRequest;
import com.vitc.dto.response.PricingPlanResponse;
import com.vitc.entity.PricingPlan;
import java.util.Arrays;
import java.util.List;

public final class PricingPlanMapper {

    private PricingPlanMapper() {
    }

    public static PricingPlan toEntity(PricingPlanRequest request) {
        PricingPlan entity = new PricingPlan();
        apply(entity, request);
        return entity;
    }

    public static void apply(PricingPlan entity, PricingPlanRequest request) {
        entity.setName(request.name());
        entity.setTagline(request.tagline());
        entity.setPrice(request.price());
        entity.setOldPrice(request.oldPrice());
        entity.setCurrency(request.currency() == null ? "INR" : request.currency());
        entity.setBillingPeriod(request.billingPeriod());
        entity.setFeatures(request.features());
        entity.setCategory(request.category());
        entity.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        entity.setHighlighted(request.highlighted() != null && request.highlighted());
        entity.setActive(request.active() == null || request.active());
    }

    public static PricingPlanResponse toResponse(PricingPlan entity) {
        return new PricingPlanResponse(
                entity.getId(),
                entity.getName(),
                entity.getTagline(),
                entity.getPrice(),
                entity.getOldPrice(),
                entity.getCurrency(),
                entity.getBillingPeriod(),
                entity.getFeatures(),
                featureList(entity.getFeatures()),
                entity.getCategory(),
                entity.getDisplayOrder(),
                entity.getHighlighted(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static List<String> featureList(String features) {
        if (features == null || features.isBlank()) {
            return List.of();
        }
        return Arrays.stream(features.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

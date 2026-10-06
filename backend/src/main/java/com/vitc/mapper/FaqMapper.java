package com.vitc.mapper;

import com.vitc.dto.request.FaqRequest;
import com.vitc.dto.response.FaqResponse;
import com.vitc.entity.Faq;

public final class FaqMapper {

    private FaqMapper() {
    }

    public static Faq toEntity(FaqRequest request) {
        Faq entity = new Faq();
        apply(entity, request);
        return entity;
    }

    public static void apply(Faq entity, FaqRequest request) {
        entity.setQuestion(request.question());
        entity.setAnswer(request.answer());
        entity.setCategory(request.category());
        entity.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        entity.setActive(request.active() == null || request.active());
    }

    public static FaqResponse toResponse(Faq entity) {
        return new FaqResponse(
                entity.getId(),
                entity.getQuestion(),
                entity.getAnswer(),
                entity.getCategory(),
                entity.getDisplayOrder(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

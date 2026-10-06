package com.vitc.mapper;

import com.vitc.dto.request.TestimonialRequest;
import com.vitc.dto.response.TestimonialResponse;
import com.vitc.entity.Testimonial;

public final class TestimonialMapper {

    private TestimonialMapper() {
    }

    public static Testimonial toEntity(TestimonialRequest request) {
        Testimonial entity = new Testimonial();
        apply(entity, request);
        return entity;
    }

    public static void apply(Testimonial entity, TestimonialRequest request) {
        entity.setName(request.name());
        entity.setRole(request.role());
        entity.setPhotoUrl(request.photoUrl());
        entity.setRating(request.rating());
        entity.setMessage(request.message());
    }

    public static TestimonialResponse toResponse(Testimonial entity) {
        return new TestimonialResponse(
                entity.getId(),
                entity.getName(),
                entity.getRole(),
                entity.getPhotoUrl(),
                entity.getRating(),
                entity.getMessage(),
                entity.getApproved(),
                entity.getCreatedAt());
    }
}

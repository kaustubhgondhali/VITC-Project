package com.vitc.mapper;

import com.vitc.dto.request.ReviewRequest;
import com.vitc.dto.response.ReviewResponse;
import com.vitc.entity.Review;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static Review toEntity(ReviewRequest request) {
        Review entity = new Review();
        apply(entity, request);
        return entity;
    }

    public static void apply(Review entity, ReviewRequest request) {
        entity.setReviewerName(request.reviewerName());
        entity.setEmail(request.email());
        entity.setCourseCode(request.courseCode());
        entity.setCourseTitle(request.courseTitle());
        entity.setRating(request.rating());
        entity.setComment(request.comment());
        entity.setImageUrl(request.imageUrl());
        entity.setApproved(request.approved() != null && request.approved());
        entity.setFeatured(request.featured() != null && request.featured());
    }

    public static ReviewResponse toResponse(Review entity) {
        return new ReviewResponse(
                entity.getId(),
                entity.getReviewerName(),
                entity.getEmail(),
                entity.getCourseCode(),
                entity.getCourseTitle(),
                entity.getRating(),
                entity.getComment(),
                entity.getImageUrl(),
                entity.getApproved(),
                entity.getFeatured(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

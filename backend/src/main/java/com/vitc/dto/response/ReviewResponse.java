package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "ReviewResponse", description = "Course review")
public record ReviewResponse(
    Long id,
    String reviewerName,
    String email,
    String courseCode,
    String courseTitle,
    Integer rating,
    String comment,
    String imageUrl,
    Boolean approved,
    Boolean featured,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

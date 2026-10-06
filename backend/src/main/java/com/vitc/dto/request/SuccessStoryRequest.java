package com.vitc.dto.request;

import jakarta.validation.constraints.*;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * {@code category} stays a validated free-text string (not a JSON enum) to match
 * every other request DTO in this codebase (see {@code GalleryItemRequest}); it is
 * additionally parsed through {@code SuccessStoryCategory.fromValue(...)} in the
 * service layer for a strongly-controlled, single source of truth.
 *
 * <p>{@code status} is intentionally NOT part of this request: a story is always
 * created as DRAFT and only moves to PUBLISHED (or back) through the dedicated
 * publish/unpublish endpoints, so publication state can never be smuggled in
 * through a generic create/update call.</p>
 */
public record SuccessStoryRequest(
    @NotBlank(message = "Name is required") @Size(max = 120) String name,
    @NotBlank(message = "Category is required") @Pattern(regexp = "(?i)student|teacher|parent",
        message = "Category must be student, teacher or parent") String category,
    @Size(max = 150) String courseProgram,
    @Positive(message = "courseId must be a positive id") Long courseId,
    @Size(max = 150) String designation,
    @Size(max = 500) String description,
    @NotBlank(message = "Video URL is required") @Size(max = 500) String videoUrl,
    @Size(max = 500) String thumbnailUrl,
    Integer displayOrder) {
}

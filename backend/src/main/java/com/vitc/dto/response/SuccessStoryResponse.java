package com.vitc.dto.response;

import java.time.LocalDateTime;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * {@code courseTitle} is resolved server-side from {@code courseId} (when set) purely
 * as a display convenience — never trust/echo a client-supplied title. {@code approved}
 * from PART 1/6 is replaced by {@code status} (DRAFT/PUBLISHED); the public "Success
 * Stories" page never reads either field, only {@code /approved} and
 * {@code /category/{cat}}, so this rename does not break the existing frontend.
 */
public record SuccessStoryResponse(
        Long id,
        String name,
        String category,
        String courseProgram,
        Long courseId,
        String courseTitle,
        String designation,
        String description,
        String videoUrl,
        String thumbnailUrl,
        String status,
        Integer displayOrder,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}

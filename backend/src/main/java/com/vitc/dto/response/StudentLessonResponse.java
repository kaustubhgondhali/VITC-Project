package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Lesson entry inside the course outline. Intentionally has NO video url -
 * protected video information is only returned by
 * {@code GET /api/v1/student/lessons/{lessonId}}.
 */
@Schema(name = "StudentLessonResponse", description = "Lesson outline entry (no video url)")
public record StudentLessonResponse(
        Long id,
        Long moduleId,
        String title,
        String description,
        String duration,
        Integer displayOrder,
        boolean completed,
        int progressPercentage,
        boolean locked,
        LocalDateTime lastWatchedAt) {
}

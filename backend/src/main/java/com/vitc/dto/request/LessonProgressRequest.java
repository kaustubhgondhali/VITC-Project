package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Progress update for one lesson. The student is taken from the session, never
 * from this payload.
 */
@Schema(name = "LessonProgressRequest", description = "Lesson progress update")
public record LessonProgressRequest(
        Boolean completed,
        @Min(0) @Max(100) Integer progressPercentage) {
}

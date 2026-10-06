package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLessonRequest(
        @NotBlank(message = "Lesson title is required")
        @Size(max = 180, message = "Title must be at most 180 characters")
        String title,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Size(max = 600, message = "Video URL must be at most 600 characters")
        String videoUrl,

        @Size(max = 40, message = "Duration must be at most 40 characters")
        String duration,

        Integer displayOrder,

        Boolean active) {
}

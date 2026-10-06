package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminModuleRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 180, message = "Title must be at most 180 characters")
        String title,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        Integer displayOrder,

        Boolean active) {
}

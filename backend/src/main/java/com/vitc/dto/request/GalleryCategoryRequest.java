package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GalleryCategoryRequest(
        @NotBlank(message = "Category name is required") @Size(max = 60) String name) {
}
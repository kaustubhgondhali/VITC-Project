package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record GalleryItemRequest(
    @NotBlank(message = "Title is required") @Size(max = 180) String title,
    @NotBlank(message = "Image URL is required") @Size(max = 400) String imageUrl,
    @Size(max = 60) String category,
    @Size(max = 500) String caption) {
}

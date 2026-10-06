package com.vitc.dto.response;

import java.time.LocalDateTime;

public record GalleryItemResponse(
        Long id,
        String title,
        String imageUrl,
        String category,
        String caption,
        LocalDateTime createdAt) {
}

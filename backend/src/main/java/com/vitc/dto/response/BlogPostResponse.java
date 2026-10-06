package com.vitc.dto.response;

import java.time.LocalDateTime;

public record BlogPostResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        String content,
        String coverImageUrl,
        String author,
        String tags,
        Boolean published,
        LocalDateTime publishedAt,
        LocalDateTime createdAt) {
}

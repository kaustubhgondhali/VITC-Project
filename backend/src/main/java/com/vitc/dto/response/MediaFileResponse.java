package com.vitc.dto.response;

import java.time.LocalDateTime;

public record MediaFileResponse(
    Long id,
    String fileName,
    String originalName,
    String contentType,
    Long sizeBytes,
    String url,
    String fileType,
    String folder,
    String uploadedBy,
    LocalDateTime createdAt) {
}

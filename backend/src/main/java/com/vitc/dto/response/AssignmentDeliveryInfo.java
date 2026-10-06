package com.vitc.dto.response;

import java.time.LocalDateTime;

/** Admin-facing summary of a delivered assignment package. Never contains the download token. */
public record AssignmentDeliveryInfo(
        String fileName,
        Long sizeBytes,
        String note,
        LocalDateTime deliveredAt,
        LocalDateTime downloadExpiresAt,
        Integer downloadCount,
        LocalDateTime lastDownloadedAt) {
}

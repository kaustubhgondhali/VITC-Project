package com.vitc.dto.response;

import java.time.LocalDateTime;

/** What a buyer's download link points at - shown on assignment-download.html. No contact details. */
public record AssignmentDownloadInfoResponse(
        String orderCode,
        String assignmentTitle,
        String fileName,
        Long sizeBytes,
        String note,
        LocalDateTime deliveredAt,
        LocalDateTime expiresAt) {
}

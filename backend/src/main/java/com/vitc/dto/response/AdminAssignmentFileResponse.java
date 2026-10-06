package com.vitc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Admin -> Assignment Upload: one catalogue assignment and its ready-made project file. */
public record AdminAssignmentFileResponse(
        Long id,
        String code,
        String title,
        BigDecimal price,
        Boolean active,
        /** Null while no project file has been uploaded for this assignment. */
        String fileName,
        Long sizeBytes,
        LocalDateTime uploadedAt,
        /** Paid orders still waiting for their files. */
        long waitingOrders,
        long deliveredOrders) {
}

package com.vitc.dto.response;

import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.EmailType;
import java.time.LocalDateTime;

/** Admin-facing view of one email delivery attempt. Contains no secrets. */
public record EmailDeliveryLogResponse(
        Long id,
        EmailType emailType,
        String recipientEmail,
        String studentLoginId,
        Long studentUserId,
        String orderCode,
        String courseTitle,
        EmailDeliveryStatus status,
        String failureReason,
        LocalDateTime sentAt,
        LocalDateTime failedAt,
        Integer retryCount) {
}

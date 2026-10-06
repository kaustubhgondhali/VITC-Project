package com.vitc.mapper;

import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.entity.EmailDeliveryLog;

public final class EmailDeliveryLogMapper {

    private EmailDeliveryLogMapper() {
    }

    public static EmailDeliveryLogResponse toResponse(EmailDeliveryLog log) {
        if (log == null) {
            return null;
        }
        return new EmailDeliveryLogResponse(
                log.getId(),
                log.getEmailType(),
                log.getRecipientEmail(),
                log.getStudentLoginId(),
                log.getStudentUserId(),
                log.getOrderCode(),
                log.getCourseTitle(),
                log.getStatus(),
                log.getFailureReason(),
                log.getSentAt(),
                log.getFailedAt(),
                log.getRetryCount());
    }
}

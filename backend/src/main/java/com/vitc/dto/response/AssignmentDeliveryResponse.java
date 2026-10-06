package com.vitc.dto.response;

import com.vitc.entity.enums.EmailDeliveryStatus;
import com.vitc.entity.enums.OrderStatus;
import java.time.LocalDateTime;

/**
 * Result of a Main Admin delivering (or re-sending) an assignment package.
 *
 * <p>{@code downloadUrl} is the only place the freshly issued link is ever returned - it is not
 * stored in plain form, so the admin can copy it from here when the email could not be sent.</p>
 */
public record AssignmentDeliveryResponse(
        String orderCode,
        OrderStatus status,
        AssignmentDeliveryInfo delivery,
        String downloadUrl,
        LocalDateTime downloadExpiresAt,
        EmailDeliveryStatus emailStatus,
        String emailFailureReason) {
}

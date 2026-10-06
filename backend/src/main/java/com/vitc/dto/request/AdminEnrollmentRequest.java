package com.vitc.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Manual (complimentary / administrative) enrolment. It never creates or
 * touches an order or a payment: the resulting enrolment is tagged so it stays
 * distinguishable from a paid one.
 */
public record AdminEnrollmentRequest(
        @NotNull(message = "Course is required") Long courseId,
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}

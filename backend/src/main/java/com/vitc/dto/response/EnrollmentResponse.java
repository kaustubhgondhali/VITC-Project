package com.vitc.dto.response;

import com.vitc.entity.enums.EnrollmentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        String studentName,
        String email,
        String phone,
        String college,
        Long courseId,
        String courseTitle,
        BigDecimal amount,
        EnrollmentStatus status,
        String notes,
        LocalDateTime createdAt) {
}

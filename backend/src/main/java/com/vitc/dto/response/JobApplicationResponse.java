package com.vitc.dto.response;

import com.vitc.entity.enums.ApplicationStatus;
import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String position,
        Integer experienceYears,
        String resumeUrl,
        String message,
        ApplicationStatus status,
        LocalDateTime createdAt) {
}

package com.vitc.dto.response;

import com.vitc.entity.enums.ApplicationStatus;
import java.time.LocalDateTime;

public record InternshipApplicationResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String college,
        String domain,
        String duration,
        String resumeUrl,
        String message,
        ApplicationStatus status,
        LocalDateTime createdAt) {
}

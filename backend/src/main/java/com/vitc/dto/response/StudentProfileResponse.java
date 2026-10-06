package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** Student profile + enrolments. Never contains password data. */
@Schema(name = "StudentProfileResponse", description = "Authenticated student profile")
public record StudentProfileResponse(
        Long id,
        String studentLoginId,
        String fullName,
        String email,
        String phone,
        String city,
        String profileImageUrl,
        String role,
        LocalDateTime memberSince,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        List<StudentCourseResponse> courses) {
}

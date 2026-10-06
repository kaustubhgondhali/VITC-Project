package com.vitc.dto.response;

import com.vitc.entity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin -> Students list row. Deliberately excludes password hashes, session
 * tokens and every other credential field of {@code User}.
 */
public record AdminStudentResponse(
        Long id,
        String studentLoginId,
        String fullName,
        String email,
        String phone,
        UserStatus status,
        List<String> purchasedCourses,
        int courseCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}

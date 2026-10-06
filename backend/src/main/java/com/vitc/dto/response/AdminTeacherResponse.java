package com.vitc.dto.response;

import com.vitc.entity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PART 10C - Teacher row for the Main Admin panel. Deliberately excludes
 * password hashes, session tokens and every other credential field of
 * {@code User}.
 */
public record AdminTeacherResponse(
        Long id,
        String fullName,
        String username,
        String email,
        String phone,
        UserStatus status,
        Boolean mustChangePassword,
        List<AdminTeacherCourseResponse> assignedCourses,
        int assignedCourseCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}

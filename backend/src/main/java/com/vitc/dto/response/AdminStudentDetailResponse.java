package com.vitc.dto.response;

import com.vitc.entity.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin -> Student details. Contains no password hash, no temporary password
 * and no session token: those fields are never read into this DTO.
 */
public record AdminStudentDetailResponse(
        Long id,
        String studentLoginId,
        String fullName,
        String email,
        String phone,
        String city,
        UserStatus status,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        List<AdminStudentCourseResponse> courses) {
}

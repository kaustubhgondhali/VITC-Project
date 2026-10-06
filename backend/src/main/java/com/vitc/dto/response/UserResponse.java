package com.vitc.dto.response;

import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "UserResponse", description = "User account (password hash never exposed)")
public record UserResponse(
    Long id,
    String fullName,
    String email,
    String phone,
    String city,
    UserRole role,
    UserStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

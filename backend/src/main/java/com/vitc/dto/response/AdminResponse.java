package com.vitc.dto.response;

import com.vitc.entity.enums.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "AdminResponse", description = "Admin account (password hash never exposed)")
public record AdminResponse(
    Long id,
    String username,
    String email,
    String fullName,
    AdminRole role,
    Boolean active,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,

    @Schema(description = "Session token, returned only by the login endpoint")
    String sessionToken
) {
}

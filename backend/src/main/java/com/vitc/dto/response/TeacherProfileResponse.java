package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Authenticated Teacher Admin profile. Never contains password data. */
@Schema(name = "TeacherProfileResponse", description = "Authenticated Teacher Admin profile")
public record TeacherProfileResponse(
        Long id,
        String username,
        String fullName,
        String email,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt) {
}

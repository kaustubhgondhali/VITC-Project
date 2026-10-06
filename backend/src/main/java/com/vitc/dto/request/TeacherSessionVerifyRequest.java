package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Verifies (or revokes) a Teacher Admin session token from a stored client session. */
public record TeacherSessionVerifyRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Token is required") String token) {
}

package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ForgotPasswordRequest", description = "Starts an admin password recovery")
public record ForgotPasswordRequest(

    @Schema(example = "admin", description = "Admin username or email address",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username or email is required")
    String usernameOrEmail
) {
}

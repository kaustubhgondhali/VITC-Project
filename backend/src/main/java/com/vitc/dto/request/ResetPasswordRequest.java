package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ResetPasswordRequest", description = "Completes an admin password recovery")
public record ResetPasswordRequest(

    @NotBlank(message = "Reset token is required")
    String token,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String newPassword
) {
}

package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "TeacherSelfResetPasswordRequest", description = "Completes a teacher self-service password recovery")
public record TeacherSelfResetPasswordRequest(

    @Schema(example = "a1b2c3d4", description = "One-time reset code issued to the teacher",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Please enter valid account details.")
    String resetCode,

    @Schema(example = "NewSecret@123", description = "New teacher password (min 8 chars)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password must be at least 8 characters.")
    @Size(min = 8, max = 64, message = "Password must be at least 8 characters.")
    String newPassword,

    @Schema(example = "NewSecret@123", description = "Confirmation of the new teacher password",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Passwords do not match.")
    String confirmPassword
) {
}


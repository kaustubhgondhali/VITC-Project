package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "TeacherForgotPasswordRequest", description = "Starts a teacher password recovery")
public record TeacherForgotPasswordRequest(

    @Schema(example = "VITCteacher", description = "Teacher username or registered email address",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Please enter your registered email/ID.")
    String usernameOrEmail
) {
}


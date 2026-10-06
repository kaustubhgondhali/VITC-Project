package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Part 4 - starts a student password recovery. Accepts either the Student ID
 * (VITCSTU#####) or the registered email, same forgiving lookup style as the
 * Main Admin "forgot password" form.
 */
@Schema(name = "StudentForgotPasswordRequest", description = "Starts a student password recovery")
public record StudentForgotPasswordRequest(

    @Schema(example = "VITCSTU10001", description = "Student ID or registered email address",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Student ID or email is required")
    String studentLoginIdOrEmail
) {
}

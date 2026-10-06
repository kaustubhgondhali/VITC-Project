package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Part 4 - completes a student password recovery using the one-time code
 * emailed to the account. The code alone identifies the account (it is
 * unique and role-scoped - see {@code UserRepository.findByResetTokenAndRole}),
 * exactly like the Main Admin reset-password step.
 */
@Schema(name = "StudentResetPasswordRequest", description = "Completes a student password recovery")
public record StudentResetPasswordRequest(

    @Schema(example = "K7XPQ2R9", description = "One-time reset code emailed to the student")
    @NotBlank(message = "Reset code is required")
    String resetCode,

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 64, message = "New password must be 8-64 characters")
    String newPassword,

    @NotBlank(message = "Please confirm the new password")
    String confirmPassword
) {
}

package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** First-login / self-service password change for a student. */
public record StudentPasswordChangeRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 64, message = "New password must be 8-64 characters")
        String newPassword,
        @NotBlank(message = "Please confirm the new password") String confirmPassword) {
}

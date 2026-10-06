package com.vitc.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
        @NotBlank(message = "Reset authorization token is required")
        @JsonAlias({"resetToken", "token", "resetCode", "recoveryToken"})
        String resetToken,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @JsonAlias({"newPassword", "password", "new_password"})
        String newPassword,

        @JsonAlias({"confirmPassword", "confirmNewPassword"})
        String confirmPassword
) {
    public CompletePasswordResetRequest {
        if (confirmPassword == null || confirmPassword.isBlank()) {
            confirmPassword = newPassword;
        }
    }
}


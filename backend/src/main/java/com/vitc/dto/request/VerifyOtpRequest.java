package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Recovery token is required")
        String recoveryToken,

        @NotBlank(message = "Please enter the 6-digit OTP")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be a 6-digit number")
        String otp
) {
}


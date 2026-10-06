package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank(message = "Recovery token is required")
        String recoveryToken
) {
}


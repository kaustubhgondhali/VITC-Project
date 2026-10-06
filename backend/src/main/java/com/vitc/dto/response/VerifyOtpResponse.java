package com.vitc.dto.response;

public record VerifyOtpResponse(
        String resetToken,
        int expiresInMinutes
) {
}


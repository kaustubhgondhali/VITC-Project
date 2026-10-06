package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Part 4 - acknowledgement that a reset code was issued and emailed. The code
 * itself is deliberately never included here (unlike the older Main Admin
 * {@code PasswordResetTokenResponse}) - it only ever leaves the backend
 * inside the email sent to the account's own registered address.
 */
@Schema(name = "StudentForgotPasswordResponse", description = "Acknowledges a student reset code was emailed")
public record StudentForgotPasswordResponse(
    String maskedEmail,
    int expiresInMinutes
) {
}

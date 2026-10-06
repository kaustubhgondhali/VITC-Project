package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "PasswordResetTokenResponse", description = "Recovery token issued for an admin account")
public record PasswordResetTokenResponse(
    String maskedEmail,
    String resetToken,
    LocalDateTime expiresAt
) {
}

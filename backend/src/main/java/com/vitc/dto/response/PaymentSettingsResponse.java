package com.vitc.dto.response;

import java.time.LocalDateTime;

/**
 * Safe view of the gateway configuration. The key secret is NEVER included —
 * only a mask so the admin UI can show that one is stored.
 */
public record PaymentSettingsResponse(
        String gateway,
        String keyId,
        String maskedKeySecret,
        boolean webhookSecretConfigured,
        String mode,
        boolean enabled,
        boolean configured,
        String status,
        String lastTestStatus,
        String lastTestMessage,
        LocalDateTime updatedAt) {
}

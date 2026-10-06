package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin payload for saving the gateway configuration.
 *
 * <p>{@code keySecret} / {@code webhookSecret} are write-only: they are
 * encrypted immediately and never returned by any endpoint. Leaving them blank
 * on an update keeps the previously stored value.</p>
 */
public record PaymentSettingsRequest(
        @NotBlank(message = "Gateway is required") String gateway,
        @Size(max = 120, message = "Key ID is too long") String keyId,
        @Size(max = 200, message = "Key secret is too long") String keySecret,
        @Size(max = 200, message = "Webhook secret is too long") String webhookSecret,
        String mode,
        Boolean enabled) {
}

package com.vitc.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vitc.entity.enums.DeliveryChannel;

public record PasswordRecoveryResponse(
        String recoveryToken,
        String targetMasked,
        int cooldownSeconds,
        int expiresInMinutes,
        DeliveryChannel channel
) {
    @JsonProperty("maskedEmail")
    public String maskedEmail() {
        return targetMasked;
    }
}

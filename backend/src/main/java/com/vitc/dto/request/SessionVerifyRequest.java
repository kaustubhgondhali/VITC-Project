package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "SessionVerifyRequest", description = "Validates an admin panel session token")
public record SessionVerifyRequest(

    @NotBlank(message = "Username is required")
    String username,

    @NotBlank(message = "Session token is required")
    String token
) {
}

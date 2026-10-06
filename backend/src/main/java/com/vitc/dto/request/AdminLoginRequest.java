package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AdminLoginRequest", description = "Admin credential verification payload")
public record AdminLoginRequest(

    @Schema(example = "vitc.admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username is required")
    @com.fasterxml.jackson.annotation.JsonAlias({"username", "email", "usernameOrEmail", "account"})
    String username,

    @Schema(example = "StrongPass@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    String password
) {
}

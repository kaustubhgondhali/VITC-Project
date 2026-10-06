package com.vitc.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryRequest(
        @NotBlank(message = "Please enter your registered email address or phone number")
        @JsonAlias({"emailOrPhone", "account", "usernameOrEmail", "studentLoginIdOrEmail", "username", "email"})
        String emailOrPhone
) {
}


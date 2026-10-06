package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Teacher Admin sign-in: username + password. */
public record TeacherLoginRequest(
        @NotBlank(message = "Username is required")
        @com.fasterxml.jackson.annotation.JsonAlias({"username", "email", "usernameOrEmail", "phone", "account"})
        String username,
        @NotBlank(message = "Password is required") String password) {
}

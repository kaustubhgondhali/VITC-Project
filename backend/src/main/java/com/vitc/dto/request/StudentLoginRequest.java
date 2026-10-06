package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Student portal sign-in: Student ID + password. */
public record StudentLoginRequest(
        @NotBlank(message = "Student ID is required")
        @com.fasterxml.jackson.annotation.JsonAlias({"studentLoginId", "studentId", "username", "email", "phone", "account"})
        String studentLoginId,
        @NotBlank(message = "Password is required") String password) {
}

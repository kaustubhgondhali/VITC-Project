package com.vitc.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin-only SMTP smoke test. */
public record TestEmailRequest(
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = "^\\S+$", message = "Enter a valid email address")
        @Size(max = 200, message = "Recipient email is too long")
        @JsonAlias({"recipient", "testRecipient"})
        String to) {
}

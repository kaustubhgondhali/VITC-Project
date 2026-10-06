package com.vitc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Load Provider Settings": the SMTP email address, plus the provider picked in the
 * dropdown (blank or AUTO = detect it from the address).
 */
public record SmtpProviderDetectRequest(
        @NotBlank(message = "Enter the SMTP email address first")
        @Email(message = "Enter a valid SMTP email address")
        @Pattern(regexp = "^\\S+$", message = "Enter a valid SMTP email address")
        @Size(max = 200, message = "SMTP email address is too long")
        String email,

        @Size(max = 40, message = "Provider is too long")
        @Pattern(regexp = "^[A-Za-z_]*$", message = "Unknown email provider")
        String provider) {
}

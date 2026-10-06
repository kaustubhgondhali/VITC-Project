package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record ContactMessageRequest(
    @NotBlank(message = "Name is required") @Size(max = 120) String name,
    @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 150) String email,
    @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid") String phone,
    @Size(max = 180) String subject,
    @NotBlank(message = "Message is required") @Size(max = 2000) String message) {
}

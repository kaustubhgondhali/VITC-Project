package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record JobApplicationRequest(
    @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
    @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
    @NotBlank(message = "Phone is required") @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid") String phone,
    @NotBlank(message = "Position is required") @Size(max = 120) String position,
    @Min(value = 0, message = "Experience cannot be negative") @Max(value = 60) Integer experienceYears,
    @Size(max = 400) String resumeUrl,
    @Size(max = 1500) String message) {
}

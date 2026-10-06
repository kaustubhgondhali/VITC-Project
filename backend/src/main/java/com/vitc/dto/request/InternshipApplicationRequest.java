package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record InternshipApplicationRequest(
    @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
    @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
    @NotBlank(message = "Phone is required") @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid") String phone,
    @Size(max = 150) String college,
    @NotBlank(message = "Domain is required") @Size(max = 120) String domain,
    @Size(max = 60) String duration,
    @Size(max = 400) String resumeUrl,
    @Size(max = 1500) String message) {
}

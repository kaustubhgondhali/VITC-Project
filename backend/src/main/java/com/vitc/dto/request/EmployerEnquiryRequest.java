package com.vitc.dto.request;

import jakarta.validation.constraints.*;

public record EmployerEnquiryRequest(
        @NotBlank @Size(max = 150) String companyName,
        @NotBlank @Size(max = 120) String contactPerson,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "^[+]?[0-9 ()-]{7,20}$") String phone,
        @NotBlank @Size(max = 150) String jobTitle,
        @PositiveOrZero Integer openings,
        @Size(max = 1000) String requiredSkills,
        @Size(max = 100) String experienceRequired,
        @Size(max = 150) String location,
        @NotBlank @Size(max = 3000) String message) {}

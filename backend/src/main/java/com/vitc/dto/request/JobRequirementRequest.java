package com.vitc.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record JobRequirementRequest(
        @NotBlank @Size(max = 150) String jobTitle,
        @NotBlank @Size(max = 150) String companyName,
        @NotBlank @Size(max = 3000) String description,
        @NotBlank @Size(max = 1000) String requiredSkills,
        @Size(max = 100) String experience,
        @Size(max = 150) String qualification,
        @Size(max = 150) String location,
        @Size(max = 80) String employmentType,
        @Size(max = 100) String salaryCtc,
        @PositiveOrZero Integer openings,
        @FutureOrPresent LocalDate applicationDeadline,
        @Size(max = 500) String applicationMethod,
        Boolean published) {}

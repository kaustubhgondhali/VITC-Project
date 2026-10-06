package com.vitc.dto.request;

import jakarta.validation.constraints.*;

public record AssignmentOrderRequest(
        @NotBlank(message = "Student name is required") @Size(max = 120) String studentName,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @NotBlank(message = "Phone is required") @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid") String phone,
        @Size(max = 150) String college,
        @NotNull(message = "Assignment id is required") Long assignmentId,
        @Size(max = 1000) String requirements) {
}

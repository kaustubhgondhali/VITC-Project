package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record TestimonialRequest(
    @NotBlank(message = "Name is required") @Size(max = 120) String name,
    @Size(max = 120) String role,
    @Size(max = 400) String photoUrl,
    @NotNull(message = "Rating is required") @Min(value = 1, message = "Rating must be at least 1") @Max(value = 5, message = "Rating must be at most 5") Integer rating,
    @NotBlank(message = "Message is required") @Size(max = 1500) String message) {
}

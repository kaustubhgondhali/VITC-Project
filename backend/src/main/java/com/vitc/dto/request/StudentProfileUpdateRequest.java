package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Part 6 - self-service profile edit. Deliberately does NOT include
 * studentLoginId, role, email, or anything about enrolment ownership - those
 * are either protected fields or resolved server-side from the session, so
 * they can never be supplied by the caller.
 */
@Schema(name = "StudentProfileUpdateRequest", description = "Editable student profile fields")
public record StudentProfileUpdateRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid")
        String phone,

        @Size(max = 120, message = "City must be at most 120 characters")
        String city) {
}

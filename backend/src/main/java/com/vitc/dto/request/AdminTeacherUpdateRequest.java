package com.vitc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PART 10C - editable Teacher profile fields. Credentials are never edited here. */
public record AdminTeacherUpdateRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 20) String phone) {
}

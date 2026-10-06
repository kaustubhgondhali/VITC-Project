package com.vitc.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PART 10C - Main Admin -&gt; Teachers: create a Teacher account.
 *
 * <p>{@code password} is optional: when omitted the backend generates a strong
 * temporary password, stores only its BCrypt hash and returns the plain value
 * exactly once to the Main Admin so it can be handed over. Plain-text
 * passwords are never persisted or logged.</p>
 */
public record AdminTeacherRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 4, max = 60) String username,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @Size(min = 8, max = 72) String password,
        List<Long> courseIds) {
}

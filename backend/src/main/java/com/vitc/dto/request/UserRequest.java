package com.vitc.dto.request;

import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UserRequest", description = "Payload to create a user account")
public record UserRequest(

    @Schema(example = "Sara Bhoir", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must not exceed 120 characters")
    String fullName,

    @Schema(example = "sara@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    String email,

    @Schema(example = "9876543210")
    @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid")
    String phone,

    @Schema(example = "StrongPass@123", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stored only as a BCrypt hash")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String password,

    @Schema(example = "Panvel")
    @Size(max = 120)
    String city,

    @Schema(example = "STUDENT", description = "Defaults to STUDENT")
    UserRole role,

    @Schema(example = "ACTIVE", description = "Defaults to ACTIVE")
    UserStatus status
) {
}

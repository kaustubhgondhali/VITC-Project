package com.vitc.dto.request;

import com.vitc.entity.enums.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminRequest", description = "Payload to create an admin account")
public record AdminRequest(

    @Schema(example = "vitc.admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 60, message = "Username must be between 4 and 60 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may contain letters, digits, dot, underscore and hyphen only")
    String username,

    @Schema(example = "admin@vitc.in", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    String email,

    @Schema(example = "Nitin Gawand", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    String fullName,

    @Schema(example = "StrongPass@123", requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stored only as a BCrypt hash")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    String password,

    @Schema(example = "ADMIN", description = "Defaults to ADMIN")
    AdminRole role,

    @Schema(example = "true", description = "Defaults to true")
    Boolean active
) {
}

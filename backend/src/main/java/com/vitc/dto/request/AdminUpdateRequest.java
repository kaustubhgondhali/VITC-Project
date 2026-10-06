package com.vitc.dto.request;

import com.vitc.entity.enums.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminUpdateRequest", description = "Payload to update an admin account (password excluded)")
public record AdminUpdateRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    String email,

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    String fullName,

    AdminRole role,

    Boolean active
) {
}

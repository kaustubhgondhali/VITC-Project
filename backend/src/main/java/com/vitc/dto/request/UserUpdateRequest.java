package com.vitc.dto.request;

import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UserUpdateRequest", description = "Payload to update a user profile (password excluded)")
public record UserUpdateRequest(

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    String email,

    @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid")
    String phone,

    @Size(max = 120)
    String city,

    UserRole role,

    UserStatus status
) {
}

package com.vitc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TeacherForgotPasswordResponse", description = "Response when a teacher initiates password recovery")
public record TeacherForgotPasswordResponse(

    @Schema(example = "t***r@example.com", description = "Masked recipient email where the code was delivered")
    String maskedEmail,

    @Schema(description = "Reset token / code issued for this request")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String resetToken,

    @Schema(example = "20", description = "Code validity window in minutes")
    Integer expiresInMinutes
) {
}


package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Successful Teacher Admin login: session token + profile. */
@Schema(name = "TeacherSessionResponse", description = "Teacher session issued after a successful login")
public record TeacherSessionResponse(
        String sessionToken,
        boolean mustChangePassword,
        TeacherProfileResponse teacher) {
}

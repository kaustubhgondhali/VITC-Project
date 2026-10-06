package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Successful student login: session token + profile. */
@Schema(name = "StudentSessionResponse", description = "Student session issued after a successful login")
public record StudentSessionResponse(
        String sessionToken,
        boolean mustChangePassword,
        StudentProfileResponse student) {
}

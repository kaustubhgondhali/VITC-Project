package com.vitc.dto.response;

/**
 * PART 10C - returned once, to the Main Admin only, right after a Teacher is
 * created or its password is reset. The plain password is never stored.
 */
public record TeacherCredentialsResponse(
        Long teacherId,
        String username,
        String temporaryPassword,
        boolean mustChangePassword) {
}

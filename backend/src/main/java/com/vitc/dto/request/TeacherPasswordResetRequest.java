package com.vitc.dto.request;

import jakarta.validation.constraints.Size;

/**
 * PART 10C - Main Admin resets a Teacher password. When {@code newPassword} is
 * null/blank a strong temporary password is generated server-side. Only the
 * BCrypt hash is stored.
 */
public record TeacherPasswordResetRequest(@Size(min = 8, max = 72) String newPassword) {
}

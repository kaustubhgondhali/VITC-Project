package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Part 6 - links a profile picture already uploaded through the existing
 * {@code POST /api/v1/files} endpoint to the authenticated student's account.
 * No binary data crosses this endpoint, only the URL the upload returned.
 */
@Schema(name = "StudentProfileImageUpdateRequest", description = "Attach an already-uploaded image URL as the profile picture")
public record StudentProfileImageUpdateRequest(

        @NotBlank(message = "Image URL is required")
        @Size(max = 255, message = "Image URL must be at most 255 characters")
        String imageUrl) {
}

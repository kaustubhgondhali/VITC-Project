package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ReviewRequest", description = "Payload to submit or update a course review")
public record ReviewRequest(

    @Schema(example = "Rohit Mhatre", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Reviewer name is required")
    @Size(max = 120, message = "Reviewer name must not exceed 120 characters")
    String reviewerName,

    @Schema(example = "rohit@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    String email,

    @Schema(example = "JAVA-FS")
    @Size(max = 60)
    String courseCode,

    @Schema(example = "Java Full Stack Development")
    @Size(max = 150)
    String courseTitle,

    @Schema(example = "5", description = "Rating between 1 and 5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    Integer rating,

    @Schema(example = "Excellent mentors and hands-on projects.", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Comment is required")
    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    String comment,

    @Schema(example = "assets/img/reviewers/rohit-mhatre.png")
    @Size(max = 500)
    String imageUrl,

    @Schema(example = "false", description = "Only admins should set this flag")
    Boolean approved,

    @Schema(example = "false")
    Boolean featured
) {
}

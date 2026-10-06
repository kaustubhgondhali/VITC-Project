package com.vitc.dto.request;

import jakarta.validation.constraints.*;


public record BlogPostRequest(
    @NotBlank(message = "Slug is required") @Size(max = 180) @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, numbers and hyphens") String slug,
    @NotBlank(message = "Title is required") @Size(max = 200) String title,
    @Size(max = 500) String excerpt,
    @NotBlank(message = "Content is required") String content,
    @Size(max = 400) String coverImageUrl,
    @Size(max = 120) String author,
    @Size(max = 300) String tags,
    Boolean published) {
}

package com.vitc.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "FaqRequest", description = "Payload to create or update a FAQ entry")
public record FaqRequest(

    @Schema(example = "What are the class timings?", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Question is required")
    @Size(max = 300, message = "Question must not exceed 300 characters")
    String question,

    @Schema(example = "Batches run from 9 AM to 8 PM on weekdays.", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Answer is required")
    @Size(max = 4000, message = "Answer must not exceed 4000 characters")
    String answer,

    @Schema(example = "Admissions")
    @Size(max = 60, message = "Category must not exceed 60 characters")
    String category,

    @Schema(example = "1", description = "Sort order used when listing FAQs")
    @Min(value = 0, message = "Display order cannot be negative")
    Integer displayOrder,

    @Schema(example = "true", description = "Defaults to true when omitted")
    Boolean active
) {
}

package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "FaqResponse", description = "FAQ entry")
public record FaqResponse(
    Long id,
    String question,
    String answer,
    String category,
    Integer displayOrder,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

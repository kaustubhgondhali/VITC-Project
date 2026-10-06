package com.vitc.dto.response;

import java.time.LocalDateTime;

public record TestimonialResponse(
        Long id,
        String name,
        String role,
        String photoUrl,
        Integer rating,
        String message,
        Boolean approved,
        LocalDateTime createdAt) {
}

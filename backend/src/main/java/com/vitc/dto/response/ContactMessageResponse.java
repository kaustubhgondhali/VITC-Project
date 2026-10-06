package com.vitc.dto.response;

import java.time.LocalDateTime;

public record ContactMessageResponse(
        Long id,
        String name,
        String email,
        String phone,
        String subject,
        String message,
        Boolean handled,
        LocalDateTime createdAt) {
}

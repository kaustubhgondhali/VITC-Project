package com.vitc.mapper;

import com.vitc.dto.request.ContactMessageRequest;
import com.vitc.dto.response.ContactMessageResponse;
import com.vitc.entity.ContactMessage;

public final class ContactMessageMapper {

    private ContactMessageMapper() {
    }

    public static ContactMessage toEntity(ContactMessageRequest request) {
        ContactMessage entity = new ContactMessage();
        apply(entity, request);
        return entity;
    }

    public static void apply(ContactMessage entity, ContactMessageRequest request) {
        entity.setName(request.name());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setSubject(request.subject());
        entity.setMessage(request.message());
    }

    public static ContactMessageResponse toResponse(ContactMessage entity) {
        return new ContactMessageResponse(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getSubject(),
                entity.getMessage(),
                entity.getHandled(),
                entity.getCreatedAt());
    }
}

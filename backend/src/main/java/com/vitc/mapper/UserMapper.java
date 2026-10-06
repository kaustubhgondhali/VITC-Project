package com.vitc.mapper;

import com.vitc.dto.request.UserRequest;
import com.vitc.dto.request.UserUpdateRequest;
import com.vitc.dto.response.UserResponse;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toEntity(UserRequest request, String passwordHash) {
        User entity = new User();
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setCity(request.city());
        entity.setPasswordHash(passwordHash);
        entity.setRole(request.role() == null ? UserRole.STUDENT : request.role());
        entity.setStatus(request.status() == null ? UserStatus.ACTIVE : request.status());
        return entity;
    }

    public static void apply(User entity, UserUpdateRequest request) {
        entity.setFullName(request.fullName());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setCity(request.city());
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
    }

    public static UserResponse toResponse(User entity) {
        return new UserResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getCity(),
                entity.getRole(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

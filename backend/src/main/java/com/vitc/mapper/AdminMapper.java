package com.vitc.mapper;

import com.vitc.dto.request.AdminRequest;
import com.vitc.dto.request.AdminUpdateRequest;
import com.vitc.dto.response.AdminResponse;
import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;

public final class AdminMapper {

    private AdminMapper() {
    }

    public static Admin toEntity(AdminRequest request, String passwordHash) {
        Admin entity = new Admin();
        entity.setUsername(request.username());
        entity.setEmail(request.email());
        entity.setFullName(request.fullName());
        entity.setPasswordHash(passwordHash);
        entity.setRole(request.role() == null ? AdminRole.ADMIN : request.role());
        entity.setActive(request.active() == null || request.active());
        return entity;
    }

    public static void apply(Admin entity, AdminUpdateRequest request) {
        entity.setEmail(request.email());
        entity.setFullName(request.fullName());
        if (request.role() != null) {
            entity.setRole(request.role());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
    }

    public static AdminResponse toResponse(Admin entity) {
        return toResponse(entity, null);
    }

    public static AdminResponse toResponse(Admin entity, String sessionToken) {
        return new AdminResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getRole(),
                entity.getActive(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                sessionToken);
    }
}

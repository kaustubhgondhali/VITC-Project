package com.vitc.mapper;

import com.vitc.dto.request.GalleryItemRequest;
import com.vitc.dto.response.GalleryItemResponse;
import com.vitc.entity.GalleryItem;

public final class GalleryItemMapper {

    private GalleryItemMapper() {
    }

    public static GalleryItem toEntity(GalleryItemRequest request) {
        GalleryItem entity = new GalleryItem();
        apply(entity, request);
        return entity;
    }

    public static void apply(GalleryItem entity, GalleryItemRequest request) {
        entity.setTitle(request.title());
        entity.setImageUrl(request.imageUrl());
        entity.setCategory(request.category());
        entity.setCaption(request.caption());
    }

    public static GalleryItemResponse toResponse(GalleryItem entity) {
        return new GalleryItemResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getImageUrl(),
                entity.getCategory(),
                entity.getCaption(),
                entity.getCreatedAt());
    }
}

package com.vitc.mapper;

import com.vitc.dto.response.MediaFileResponse;
import com.vitc.entity.MediaFile;

public final class MediaFileMapper {

    private MediaFileMapper() {
    }

    public static MediaFileResponse toResponse(MediaFile entity) {
        return new MediaFileResponse(
                entity.getId(),
                entity.getFileName(),
                entity.getOriginalName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getUrl(),
                entity.getFileType(),
                entity.getFolder(),
                entity.getUploadedBy(),
                entity.getCreatedAt());
    }
}

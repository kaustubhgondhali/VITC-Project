package com.vitc.dto.response;

/** The uploaded success-story video and the optional frame extracted from that video. */
public record SuccessStoryVideoUploadResponse(
        String url,
        String thumbnailUrl) {
}

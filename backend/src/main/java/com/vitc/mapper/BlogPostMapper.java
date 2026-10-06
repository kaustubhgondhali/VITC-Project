package com.vitc.mapper;

import com.vitc.dto.request.BlogPostRequest;
import com.vitc.dto.response.BlogPostResponse;
import com.vitc.entity.BlogPost;

public final class BlogPostMapper {

    private BlogPostMapper() {
    }

    public static BlogPost toEntity(BlogPostRequest request) {
        BlogPost entity = new BlogPost();
        apply(entity, request);
        return entity;
    }

    public static void apply(BlogPost entity, BlogPostRequest request) {
        entity.setSlug(request.slug());
        entity.setTitle(request.title());
        entity.setExcerpt(request.excerpt());
        entity.setContent(request.content());
        entity.setCoverImageUrl(request.coverImageUrl());
        entity.setAuthor(request.author());
        entity.setTags(request.tags());
        entity.setPublished(request.published() == null || request.published());
    }

    public static BlogPostResponse toResponse(BlogPost entity) {
        return new BlogPostResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getExcerpt(),
                entity.getContent(),
                entity.getCoverImageUrl(),
                entity.getAuthor(),
                entity.getTags(),
                entity.getPublished(),
                entity.getPublishedAt(),
                entity.getCreatedAt());
    }
}

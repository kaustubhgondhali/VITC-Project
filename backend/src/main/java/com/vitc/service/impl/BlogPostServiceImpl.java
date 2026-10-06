package com.vitc.service.impl;

import com.vitc.dto.request.BlogPostRequest;
import com.vitc.dto.response.BlogPostResponse;
import com.vitc.entity.BlogPost;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.BlogPostMapper;
import com.vitc.repository.BlogPostRepository;
import com.vitc.service.BlogPostService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogPostServiceImpl implements BlogPostService {

    private final BlogPostRepository repository;

    @Override
    public List<BlogPostResponse> getAll() {
        return repository.findAll().stream().map(BlogPostMapper::toResponse).toList();
    }

    @Override
    public BlogPostResponse getById(Long id) {
        return BlogPostMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public BlogPostResponse create(BlogPostRequest request) {
        if (repository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("BlogPost already exists with slug: " + request.slug());
        }
        BlogPost entity = BlogPostMapper.toEntity(request);
        if (Boolean.TRUE.equals(entity.getPublished()) && entity.getPublishedAt() == null) {
            entity.setPublishedAt(java.time.LocalDateTime.now());
        }
        return BlogPostMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public BlogPostResponse update(Long id, BlogPostRequest request) {
        BlogPost entity = find(id);
        if (!entity.getSlug().equals(request.slug()) && repository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("BlogPost already exists with slug: " + request.slug());
        }
        BlogPostMapper.apply(entity, request);
        if (Boolean.TRUE.equals(entity.getPublished()) && entity.getPublishedAt() == null) {
            entity.setPublishedAt(java.time.LocalDateTime.now());
        }
        return BlogPostMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<BlogPostResponse> getPublished() {
        return repository.findByPublishedTrueOrderByPublishedAtDesc().stream()
                .map(BlogPostMapper::toResponse).toList();
    }

    @Override
    public BlogPostResponse getBySlug(String slug) {
        return repository.findBySlug(slug).map(BlogPostMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Blog post not found with slug: " + slug));
    }

    private BlogPost find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BlogPost", id));
    }
}

package com.vitc.service;

import com.vitc.dto.request.BlogPostRequest;
import com.vitc.dto.response.BlogPostResponse;
import java.util.List;

public interface BlogPostService {

    List<BlogPostResponse> getAll();

    BlogPostResponse getById(Long id);

    BlogPostResponse create(BlogPostRequest request);

    BlogPostResponse update(Long id, BlogPostRequest request);

    void delete(Long id);

    List<BlogPostResponse> getPublished();

    BlogPostResponse getBySlug(String slug);
}

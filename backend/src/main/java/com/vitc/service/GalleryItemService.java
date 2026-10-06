package com.vitc.service;

import com.vitc.dto.request.GalleryItemRequest;
import com.vitc.dto.response.GalleryItemResponse;
import java.util.List;

public interface GalleryItemService {

    List<GalleryItemResponse> getAll();

    GalleryItemResponse getById(Long id);

    GalleryItemResponse create(GalleryItemRequest request);

    GalleryItemResponse update(Long id, GalleryItemRequest request);

    void delete(Long id);

    List<GalleryItemResponse> getByCategory(String category);
}

package com.vitc.service.impl;

import com.vitc.dto.request.GalleryItemRequest;
import com.vitc.dto.response.GalleryItemResponse;
import com.vitc.entity.GalleryItem;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.GalleryItemMapper;
import com.vitc.repository.GalleryItemRepository;
import com.vitc.service.GalleryItemService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GalleryItemServiceImpl implements GalleryItemService {

    private final GalleryItemRepository repository;

    @Override
    public List<GalleryItemResponse> getAll() {
        return repository.findAll().stream().map(GalleryItemMapper::toResponse).toList();
    }

    @Override
    public GalleryItemResponse getById(Long id) {
        return GalleryItemMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public GalleryItemResponse create(GalleryItemRequest request) {
        GalleryItem entity = GalleryItemMapper.toEntity(request);
        return GalleryItemMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public GalleryItemResponse update(Long id, GalleryItemRequest request) {
        GalleryItem entity = find(id);
        GalleryItemMapper.apply(entity, request);
        return GalleryItemMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<GalleryItemResponse> getByCategory(String category) {
        return repository.findByCategoryIgnoreCase(category).stream().map(GalleryItemMapper::toResponse).toList();
    }

    private GalleryItem find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GalleryItem", id));
    }
}

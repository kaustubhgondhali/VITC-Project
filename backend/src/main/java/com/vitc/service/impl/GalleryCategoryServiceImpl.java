package com.vitc.service.impl;

import com.vitc.dto.request.GalleryCategoryRequest;
import com.vitc.entity.GalleryCategory;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.repository.GalleryCategoryRepository;
import com.vitc.repository.GalleryItemRepository;
import com.vitc.service.GalleryCategoryService;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GalleryCategoryServiceImpl implements GalleryCategoryService {

    private final GalleryCategoryRepository categoryRepository;
    private final GalleryItemRepository itemRepository;

    @Override
    public List<String> getAll() {
        List<String> names = new ArrayList<>();
        for (GalleryCategory category : categoryRepository.findAll()) {
            addName(names, category.getName());
        }
        itemRepository.findAll().forEach(item -> addName(names, item.getCategory()));
        names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return names;
    }

    @Override
    @Transactional
    public String create(GalleryCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Gallery category already exists");
        }
        if (itemRepository.findAll().stream().anyMatch(item -> item.getCategory() != null
                && item.getCategory().trim().equalsIgnoreCase(name))) {
            throw new DuplicateResourceException("Gallery category already exists");
        }
        return categoryRepository.save(new GalleryCategory(name)).getName();
    }

    private void addName(List<String> names, String rawName) {
        if (rawName == null) return;
        String name = rawName.trim();
        if (!name.isEmpty() && names.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
            names.add(name);
        }
    }
}
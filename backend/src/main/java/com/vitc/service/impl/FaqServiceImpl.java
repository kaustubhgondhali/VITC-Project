package com.vitc.service.impl;

import com.vitc.dto.request.FaqRequest;
import com.vitc.dto.response.FaqResponse;
import com.vitc.entity.Faq;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.FaqMapper;
import com.vitc.repository.FaqRepository;
import com.vitc.service.FaqService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqServiceImpl implements FaqService {

    private final FaqRepository repository;

    @Override
    public List<FaqResponse> getAll() {
        return repository.findAll().stream().map(FaqMapper::toResponse).toList();
    }

    @Override
    public FaqResponse getById(Long id) {
        return FaqMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public FaqResponse create(FaqRequest request) {
        if (repository.existsByQuestionIgnoreCase(request.question())) {
            throw new DuplicateResourceException("FAQ already exists with question: " + request.question());
        }
        return FaqMapper.toResponse(repository.save(FaqMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public FaqResponse update(Long id, FaqRequest request) {
        Faq entity = find(id);
        if (!entity.getQuestion().equalsIgnoreCase(request.question())
                && repository.existsByQuestionIgnoreCase(request.question())) {
            throw new DuplicateResourceException("FAQ already exists with question: " + request.question());
        }
        FaqMapper.apply(entity, request);
        return FaqMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<FaqResponse> getActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream().map(FaqMapper::toResponse).toList();
    }

    @Override
    public List<FaqResponse> getByCategory(String category) {
        return repository.findByCategoryIgnoreCaseOrderByDisplayOrderAsc(category)
                .stream().map(FaqMapper::toResponse).toList();
    }

    @Override
    public List<FaqResponse> search(String keyword) {
        return repository.findByQuestionContainingIgnoreCaseOrAnswerContainingIgnoreCase(keyword, keyword)
                .stream().map(FaqMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public FaqResponse updateActive(Long id, boolean active) {
        Faq entity = find(id);
        entity.setActive(active);
        return FaqMapper.toResponse(repository.save(entity));
    }

    private Faq find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Faq", id));
    }
}

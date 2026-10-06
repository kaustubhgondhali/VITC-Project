package com.vitc.service.impl;

import com.vitc.dto.request.TestimonialRequest;
import com.vitc.dto.response.TestimonialResponse;
import com.vitc.entity.Testimonial;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.TestimonialMapper;
import com.vitc.repository.TestimonialRepository;
import com.vitc.service.TestimonialService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestimonialServiceImpl implements TestimonialService {

    private final TestimonialRepository repository;

    @Override
    public List<TestimonialResponse> getAll() {
        return repository.findAll().stream().map(TestimonialMapper::toResponse).toList();
    }

    @Override
    public TestimonialResponse getById(Long id) {
        return TestimonialMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public TestimonialResponse create(TestimonialRequest request) {
        Testimonial entity = TestimonialMapper.toEntity(request);
        return TestimonialMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public TestimonialResponse update(Long id, TestimonialRequest request) {
        Testimonial entity = find(id);
        TestimonialMapper.apply(entity, request);
        return TestimonialMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<TestimonialResponse> getApproved() {
        return repository.findByApprovedTrueOrderByCreatedAtDesc().stream()
                .map(TestimonialMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public TestimonialResponse setApproved(Long id, boolean approved) {
        Testimonial entity = find(id);
        entity.setApproved(approved);
        return TestimonialMapper.toResponse(repository.save(entity));
    }

    private Testimonial find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial", id));
    }
}

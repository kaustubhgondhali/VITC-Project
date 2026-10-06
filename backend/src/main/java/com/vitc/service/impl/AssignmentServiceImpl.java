package com.vitc.service.impl;

import com.vitc.dto.request.AssignmentRequest;
import com.vitc.dto.response.AssignmentResponse;
import com.vitc.entity.Assignment;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.AssignmentMapper;
import com.vitc.repository.AssignmentRepository;
import com.vitc.service.AssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository repository;

    @Override
    public List<AssignmentResponse> getAll() {
        return repository.findAll().stream().map(AssignmentMapper::toResponse).toList();
    }

    @Override
    public AssignmentResponse getById(Long id) {
        return AssignmentMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public AssignmentResponse create(AssignmentRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Assignment already exists with code: " + request.code());
        }
        Assignment entity = AssignmentMapper.toEntity(request);
        return AssignmentMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public AssignmentResponse update(Long id, AssignmentRequest request) {
        Assignment entity = find(id);
        if (!entity.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Assignment already exists with code: " + request.code());
        }
        AssignmentMapper.apply(entity, request);
        return AssignmentMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<AssignmentResponse> getActive() {
        return repository.findByActiveTrue().stream().map(AssignmentMapper::toResponse).toList();
    }

    @Override
    public AssignmentResponse getByCode(String code) {
        return repository.findByCode(code).map(AssignmentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with code: " + code));
    }

    @Override
    public List<AssignmentResponse> getByCategory(String category) {
        return repository.findByCategoryIgnoreCase(category).stream().map(AssignmentMapper::toResponse).toList();
    }

    private Assignment find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }
}

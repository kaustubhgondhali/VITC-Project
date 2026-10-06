package com.vitc.service.impl;

import com.vitc.dto.request.InternshipApplicationRequest;
import com.vitc.dto.response.InternshipApplicationResponse;
import com.vitc.entity.InternshipApplication;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.InternshipApplicationMapper;
import com.vitc.repository.InternshipApplicationRepository;
import com.vitc.service.InternshipApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternshipApplicationServiceImpl implements InternshipApplicationService {

    private final InternshipApplicationRepository repository;

    @Override
    public List<InternshipApplicationResponse> getAll() {
        return repository.findAll().stream().map(InternshipApplicationMapper::toResponse).toList();
    }

    @Override
    public InternshipApplicationResponse getById(Long id) {
        return InternshipApplicationMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public InternshipApplicationResponse create(InternshipApplicationRequest request) {
        InternshipApplication entity = InternshipApplicationMapper.toEntity(request);
        return InternshipApplicationMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public InternshipApplicationResponse update(Long id, InternshipApplicationRequest request) {
        InternshipApplication entity = find(id);
        InternshipApplicationMapper.apply(entity, request);
        return InternshipApplicationMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<InternshipApplicationResponse> getByStatus(ApplicationStatus status) {
        return repository.findByStatus(status).stream().map(InternshipApplicationMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public InternshipApplicationResponse updateStatus(Long id, ApplicationStatus status) {
        InternshipApplication entity = find(id);
        entity.setStatus(status);
        return InternshipApplicationMapper.toResponse(repository.save(entity));
    }

    private InternshipApplication find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipApplication", id));
    }
}

package com.vitc.service.impl;

import com.vitc.dto.request.JobApplicationRequest;
import com.vitc.dto.response.JobApplicationResponse;
import com.vitc.entity.JobApplication;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.JobApplicationMapper;
import com.vitc.repository.JobApplicationRepository;
import com.vitc.service.JobApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository repository;

    @Override
    public List<JobApplicationResponse> getAll() {
        return repository.findAll().stream().map(JobApplicationMapper::toResponse).toList();
    }

    @Override
    public JobApplicationResponse getById(Long id) {
        return JobApplicationMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public JobApplicationResponse create(JobApplicationRequest request) {
        JobApplication entity = JobApplicationMapper.toEntity(request);
        return JobApplicationMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public JobApplicationResponse update(Long id, JobApplicationRequest request) {
        JobApplication entity = find(id);
        JobApplicationMapper.apply(entity, request);
        return JobApplicationMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    @Override
    public List<JobApplicationResponse> getByStatus(ApplicationStatus status) {
        return repository.findByStatus(status).stream().map(JobApplicationMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public JobApplicationResponse updateStatus(Long id, ApplicationStatus status) {
        JobApplication entity = find(id);
        entity.setStatus(status);
        return JobApplicationMapper.toResponse(repository.save(entity));
    }

    private JobApplication find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobApplication", id));
    }
}

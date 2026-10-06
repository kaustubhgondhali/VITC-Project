package com.vitc.service.impl;

import com.vitc.dto.request.PricingPlanRequest;
import com.vitc.dto.response.PricingPlanResponse;
import com.vitc.entity.PricingPlan;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.PricingPlanMapper;
import com.vitc.repository.PricingPlanRepository;
import com.vitc.service.PricingPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingPlanServiceImpl implements PricingPlanService {

    private final PricingPlanRepository repository;

    @Override
    public List<PricingPlanResponse> getAll() {
        return repository.findAllByOrderByDisplayOrderAsc().stream().map(PricingPlanMapper::toResponse).toList();
    }

    @Override
    public List<PricingPlanResponse> getActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream().map(PricingPlanMapper::toResponse).toList();
    }

    @Override
    public List<PricingPlanResponse> getByCategory(String category) {
        return repository.findByCategoryIgnoreCaseOrderByDisplayOrderAsc(category).stream()
                .map(PricingPlanMapper::toResponse).toList();
    }

    @Override
    public PricingPlanResponse getById(Long id) {
        return PricingPlanMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public PricingPlanResponse create(PricingPlanRequest request) {
        return PricingPlanMapper.toResponse(repository.save(PricingPlanMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public PricingPlanResponse update(Long id, PricingPlanRequest request) {
        PricingPlan entity = find(id);
        PricingPlanMapper.apply(entity, request);
        return PricingPlanMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public PricingPlanResponse updateActive(Long id, boolean active) {
        PricingPlan entity = find(id);
        entity.setActive(active);
        return PricingPlanMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private PricingPlan find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PricingPlan", id));
    }
}

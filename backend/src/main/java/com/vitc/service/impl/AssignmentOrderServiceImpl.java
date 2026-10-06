package com.vitc.service.impl;

import com.vitc.dto.request.AssignmentOrderRequest;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.entity.Assignment;
import com.vitc.entity.AssignmentOrder;
import com.vitc.entity.enums.OrderStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.AssignmentOrderMapper;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.AssignmentRepository;
import com.vitc.service.AssignmentOrderService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentOrderServiceImpl implements AssignmentOrderService {

    private final AssignmentOrderRepository repository;
    private final AssignmentRepository assignmentRepository;

    @Override
    public List<AssignmentOrderResponse> getAll() {
        return repository.findAll().stream().map(AssignmentOrderMapper::toResponse).toList();
    }

    @Override
    public AssignmentOrderResponse getById(Long id) {
        return AssignmentOrderMapper.toResponse(find(id));
    }

    @Override
    public AssignmentOrderResponse getByOrderCode(String orderCode) {
        return repository.findByOrderCode(orderCode).map(AssignmentOrderMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with code: " + orderCode));
    }

    @Override
    @Transactional
    public AssignmentOrderResponse create(AssignmentOrderRequest request) {
        Assignment assignment = assignment(request.assignmentId());
        if (Boolean.FALSE.equals(assignment.getActive())) {
            throw new BadRequestException("Assignment is not available: " + assignment.getTitle());
        }
        AssignmentOrder entity = AssignmentOrderMapper.toEntity(request, assignment, generateOrderCode());
        return AssignmentOrderMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public AssignmentOrderResponse update(Long id, AssignmentOrderRequest request) {
        AssignmentOrder entity = find(id);
        AssignmentOrderMapper.apply(entity, request, assignment(request.assignmentId()));
        return AssignmentOrderMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public AssignmentOrderResponse updateStatus(Long id, OrderStatus status) {
        AssignmentOrder entity = find(id);
        entity.setStatus(status);
        return AssignmentOrderMapper.toResponse(repository.save(entity));
    }

    @Override
    public List<AssignmentOrderResponse> getByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).stream().map(AssignmentOrderMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private String generateOrderCode() {
        String code;
        do {
            code = "VITC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (repository.existsByOrderCode(code));
        return code;
    }

    private AssignmentOrder find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AssignmentOrder", id));
    }

    private Assignment assignment(Long id) {
        return assignmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }
}

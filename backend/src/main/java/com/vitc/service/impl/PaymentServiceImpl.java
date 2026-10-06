package com.vitc.service.impl;

import com.vitc.dto.request.PaymentRequest;
import com.vitc.dto.response.PaymentResponse;
import com.vitc.entity.Payment;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.OrderStatus;
import com.vitc.entity.enums.PaymentStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.PaymentMapper;
import com.vitc.repository.AssignmentOrderRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.PaymentRepository;
import com.vitc.service.PaymentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentOrderRepository assignmentOrderRepository;

    @Override
    public List<PaymentResponse> getAll() {
        return repository.findAll().stream().map(PaymentMapper::toResponse).toList();
    }

    @Override
    public PaymentResponse getById(Long id) {
        return PaymentMapper.toResponse(find(id));
    }

    @Override
    public PaymentResponse getByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with transaction: " + transactionId));
    }

    @Override
    @Transactional
    public PaymentResponse initiate(PaymentRequest request) {
        validateReference(request.referenceType().toUpperCase(), request.referenceId());
        String txn = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return PaymentMapper.toResponse(repository.save(PaymentMapper.toEntity(request, txn)));
    }

    @Override
    @Transactional
    public PaymentResponse update(Long id, PaymentRequest request) {
        Payment payment = find(id);
        validateReference(request.referenceType().toUpperCase(), request.referenceId());
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("A settled payment can no longer be modified");
        }
        payment.setReferenceType(request.referenceType().toUpperCase());
        payment.setReferenceId(request.referenceId());
        payment.setPayerName(request.payerName());
        payment.setEmail(request.email());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        return PaymentMapper.toResponse(repository.save(payment));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Payment payment = find(id);
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("A successful payment cannot be deleted; refund it instead");
        }
        repository.delete(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updateStatus(Long id, PaymentStatus status) {
        Payment payment = find(id);
        payment.setStatus(status);
        Payment saved = repository.save(payment);
        if (status == PaymentStatus.SUCCESS) {
            confirmReference(saved.getReferenceType(), saved.getReferenceId());
        }
        return PaymentMapper.toResponse(saved);
    }

    @Override
    public List<PaymentResponse> getByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).stream().map(PaymentMapper::toResponse).toList();
    }

    private void validateReference(String type, Long id) {
        if ("COURSE".equals(type)) {
            enrollmentRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Enrollment", id));
        } else {
            assignmentOrderRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("AssignmentOrder", id));
        }
    }

    private void confirmReference(String type, Long id) {
        if ("COURSE".equals(type)) {
            enrollmentRepository.findById(id).ifPresent(enrollment -> {
                enrollment.setStatus(EnrollmentStatus.CONFIRMED);
                enrollmentRepository.save(enrollment);
            });
        } else {
            assignmentOrderRepository.findById(id).ifPresent(order -> {
                order.setStatus(OrderStatus.IN_PROGRESS);
                assignmentOrderRepository.save(order);
            });
        }
    }

    private Payment find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }
}

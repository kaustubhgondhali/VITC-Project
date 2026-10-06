package com.vitc.service.impl;

import com.vitc.dto.response.InvoiceResponse;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.InvoiceMapper;
import com.vitc.repository.InvoiceRepository;
import com.vitc.service.InvoiceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository repository;

    @Override
    public List<InvoiceResponse> getAll() {
        return repository.findAll().stream().map(InvoiceMapper::toResponse).toList();
    }

    @Override
    public InvoiceResponse getById(Long id) {
        return repository.findById(id).map(InvoiceMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Override
    public InvoiceResponse getByNumber(String invoiceNumber) {
        return repository.findByInvoiceNumber(invoiceNumber).map(InvoiceMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceNumber));
    }

    @Override
    public InvoiceResponse getByOrderCode(String orderCode) {
        return repository.findByOrderOrderCode(orderCode).map(InvoiceMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for order: " + orderCode));
    }

    @Override
    public List<InvoiceResponse> getByEmail(String email) {
        return repository.findByBillingEmailIgnoreCaseOrderByIdDesc(email).stream()
                .map(InvoiceMapper::toResponse).toList();
    }
}

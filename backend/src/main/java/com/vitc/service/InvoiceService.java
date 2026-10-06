package com.vitc.service;

import com.vitc.dto.response.InvoiceResponse;
import java.util.List;

public interface InvoiceService {

    List<InvoiceResponse> getAll();

    InvoiceResponse getById(Long id);

    InvoiceResponse getByNumber(String invoiceNumber);

    InvoiceResponse getByOrderCode(String orderCode);

    List<InvoiceResponse> getByEmail(String email);
}

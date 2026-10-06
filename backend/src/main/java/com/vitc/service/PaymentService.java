package com.vitc.service;

import com.vitc.dto.request.PaymentRequest;
import com.vitc.dto.response.PaymentResponse;
import com.vitc.entity.enums.PaymentStatus;
import java.util.List;

public interface PaymentService {

    List<PaymentResponse> getAll();

    PaymentResponse getById(Long id);

    PaymentResponse getByTransactionId(String transactionId);

    PaymentResponse initiate(PaymentRequest request);

    PaymentResponse update(Long id, PaymentRequest request);

    PaymentResponse updateStatus(Long id, PaymentStatus status);

    void delete(Long id);

    List<PaymentResponse> getByEmail(String email);
}

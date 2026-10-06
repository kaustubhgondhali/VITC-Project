package com.vitc.service;

import com.vitc.dto.request.AssignmentOrderRequest;
import com.vitc.dto.response.AssignmentOrderResponse;
import com.vitc.entity.enums.OrderStatus;
import java.util.List;

public interface AssignmentOrderService {

    List<AssignmentOrderResponse> getAll();

    AssignmentOrderResponse getById(Long id);

    AssignmentOrderResponse getByOrderCode(String orderCode);

    AssignmentOrderResponse create(AssignmentOrderRequest request);

    AssignmentOrderResponse update(Long id, AssignmentOrderRequest request);

    AssignmentOrderResponse updateStatus(Long id, OrderStatus status);

    List<AssignmentOrderResponse> getByEmail(String email);

    void delete(Long id);
}

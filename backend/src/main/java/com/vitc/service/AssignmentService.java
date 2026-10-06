package com.vitc.service;

import com.vitc.dto.request.AssignmentRequest;
import com.vitc.dto.response.AssignmentResponse;
import java.util.List;

public interface AssignmentService {

    List<AssignmentResponse> getAll();

    AssignmentResponse getById(Long id);

    AssignmentResponse create(AssignmentRequest request);

    AssignmentResponse update(Long id, AssignmentRequest request);

    void delete(Long id);

    List<AssignmentResponse> getActive();

    AssignmentResponse getByCode(String code);

    List<AssignmentResponse> getByCategory(String category);
}

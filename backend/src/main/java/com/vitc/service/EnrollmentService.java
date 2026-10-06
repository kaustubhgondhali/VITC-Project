package com.vitc.service;

import com.vitc.dto.request.EnrollmentRequest;
import com.vitc.dto.response.EnrollmentResponse;
import com.vitc.entity.enums.EnrollmentStatus;
import java.util.List;

public interface EnrollmentService {

    List<EnrollmentResponse> getAll();

    EnrollmentResponse getById(Long id);

    EnrollmentResponse create(EnrollmentRequest request);

    EnrollmentResponse update(Long id, EnrollmentRequest request);

    EnrollmentResponse updateStatus(Long id, EnrollmentStatus status);

    List<EnrollmentResponse> getByEmail(String email);

    List<EnrollmentResponse> getByCourse(Long courseId);

    void delete(Long id);
}

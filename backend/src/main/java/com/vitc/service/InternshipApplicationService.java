package com.vitc.service;

import com.vitc.dto.request.InternshipApplicationRequest;
import com.vitc.dto.response.InternshipApplicationResponse;
import com.vitc.entity.enums.ApplicationStatus;
import java.util.List;

public interface InternshipApplicationService {

    List<InternshipApplicationResponse> getAll();

    InternshipApplicationResponse getById(Long id);

    InternshipApplicationResponse create(InternshipApplicationRequest request);

    InternshipApplicationResponse update(Long id, InternshipApplicationRequest request);

    void delete(Long id);

    List<InternshipApplicationResponse> getByStatus(ApplicationStatus status);

    InternshipApplicationResponse updateStatus(Long id, ApplicationStatus status);
}

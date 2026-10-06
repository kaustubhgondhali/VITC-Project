package com.vitc.service;

import com.vitc.dto.request.JobApplicationRequest;
import com.vitc.dto.response.JobApplicationResponse;
import com.vitc.entity.enums.ApplicationStatus;
import java.util.List;

public interface JobApplicationService {

    List<JobApplicationResponse> getAll();

    JobApplicationResponse getById(Long id);

    JobApplicationResponse create(JobApplicationRequest request);

    JobApplicationResponse update(Long id, JobApplicationRequest request);

    void delete(Long id);

    List<JobApplicationResponse> getByStatus(ApplicationStatus status);

    JobApplicationResponse updateStatus(Long id, ApplicationStatus status);
}

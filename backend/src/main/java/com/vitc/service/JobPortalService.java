package com.vitc.service;

import com.vitc.dto.request.*;
import com.vitc.dto.response.*;
import java.util.List;

public interface JobPortalService {
    List<JobRequirementResponse> publicJobs();
    List<JobRequirementResponse> allJobs();
    JobRequirementResponse save(Long id, JobRequirementRequest request);
    void deleteJob(Long id);
    EmployerEnquiryResponse submit(EmployerEnquiryRequest request);
    List<EmployerEnquiryResponse> enquiries();
    EmployerEnquiryResponse updateEnquiry(Long id, EnquiryStatusRequest request);
    void deleteEnquiry(Long id);
}

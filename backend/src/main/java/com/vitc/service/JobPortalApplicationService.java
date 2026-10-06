package com.vitc.service;

import com.vitc.dto.request.JobApplicationStatusRequest;
import com.vitc.dto.request.JobPortalApplicationRequest;
import com.vitc.dto.response.AdminJobApplicationResponse;
import com.vitc.dto.response.JobApplicationSubmittedResponse;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Job Portal applications: a visitor applies to a published job with their details and resume;
 * Main Admin reviews them per job and company. The resume is stored privately (never under the
 * public /uploads folder) and only streamed to a signed-in Main Admin.
 */
public interface JobPortalApplicationService {

    /** Public: apply to a published, still-open job. One application per email per job. */
    JobApplicationSubmittedResponse apply(Long jobId, JobPortalApplicationRequest request, MultipartFile resume);

    /** Admin: every job application, newest first, including older general career applications. */
    List<AdminJobApplicationResponse> list();

    /** Admin: move an application through the hiring pipeline, with optional recruiter notes. */
    AdminJobApplicationResponse updateStatus(Long id, JobApplicationStatusRequest request);

    /** Admin: the applicant's resume for download. */
    ResumeDownload openResume(Long id);

    /** Admin: deletes the application and its resume file. */
    void delete(Long id);

    record ResumeDownload(Resource resource, String fileName, String contentType, long sizeBytes) {
    }
}

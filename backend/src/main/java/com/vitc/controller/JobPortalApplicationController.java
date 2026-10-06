package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.JobPortalApplicationRequest;
import com.vitc.dto.response.JobApplicationSubmittedResponse;
import com.vitc.service.JobPortalApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Job Portal -> Apply. Public on purpose (job seekers need not be VITC students); abuse is
 * limited by the "job-application" rate-limit rule and one application per email per job.
 */
@Tag(name = "Job Portal", description = "Apply to published job requirements")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jobs")
public class JobPortalApplicationController {

    private final JobPortalApplicationService service;

    @Operation(summary = "Apply to a published job with personal details and a resume (multipart)")
    @PostMapping(path = "/{jobId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JobApplicationSubmittedResponse>> apply(
            @PathVariable Long jobId,
            @Valid @ModelAttribute JobPortalApplicationRequest request,
            // Optional here only so a missing file gets a friendly message from the service, not a 500.
            @RequestPart(value = "resume", required = false) MultipartFile resume) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted", service.apply(jobId, request, resume)));
    }
}

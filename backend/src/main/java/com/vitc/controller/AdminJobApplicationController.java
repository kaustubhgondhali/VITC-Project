package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.JobApplicationStatusRequest;
import com.vitc.dto.response.AdminJobApplicationResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.JobPortalApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin -> Job Applications. Under /api/v1/admin/**, so every call needs a Main Admin session. */
@Tag(name = "Admin - Job applications", description = "Review Job Portal applications and resumes")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/job-applications")
public class AdminJobApplicationController {

    private final JobPortalApplicationService service;

    @Operation(summary = "List all job applications, newest first")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminJobApplicationResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @Operation(summary = "Update an application's status and recruiter notes")
    @RequireRole(Role.MAIN_ADMIN)
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminJobApplicationResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody JobApplicationStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Application updated", service.updateStatus(id, request)));
    }

    @Operation(summary = "Download the applicant's resume")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}/resume")
    public ResponseEntity<Resource> resume(@PathVariable Long id) {
        JobPortalApplicationService.ResumeDownload resume = service.openResume(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resume.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(resume.contentType()))
                .contentLength(resume.sizeBytes())
                .body(resume.resource());
    }

    @Operation(summary = "Delete an application and its resume")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Application deleted"));
    }
}

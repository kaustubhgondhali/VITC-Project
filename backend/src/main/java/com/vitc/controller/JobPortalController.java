package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.EmployerEnquiryRequest;
import com.vitc.dto.request.JobRequirementRequest;
import com.vitc.dto.response.*;
import com.vitc.service.JobPortalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1")
public class JobPortalController {
    private final JobPortalService service;
    @GetMapping("/jobs") public ResponseEntity<ApiResponse<List<JobRequirementResponse>>> publicJobs() { return ResponseEntity.ok(ApiResponse.ok(service.publicJobs())); }
    @PostMapping("/employer/enquiries") public ResponseEntity<ApiResponse<EmployerEnquiryResponse>> submit(@Valid @RequestBody EmployerEnquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Enquiry submitted", service.submit(request)));
    }
    @GetMapping("/admin/jobs") public ResponseEntity<ApiResponse<List<JobRequirementResponse>>> jobs() { return ResponseEntity.ok(ApiResponse.ok(service.allJobs())); }
    @PostMapping("/admin/jobs") public ResponseEntity<ApiResponse<JobRequirementResponse>> create(@Valid @RequestBody JobRequirementRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Job created", service.save(null, request))); }
    @PutMapping("/admin/jobs/{id}") public ResponseEntity<ApiResponse<JobRequirementResponse>> update(@PathVariable Long id, @Valid @RequestBody JobRequirementRequest request) { return ResponseEntity.ok(ApiResponse.ok(service.save(id, request))); }
    @DeleteMapping("/admin/jobs/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { service.deleteJob(id); return ResponseEntity.ok(ApiResponse.message("Job deleted")); }
    @GetMapping("/admin/employer-enquiries") public ResponseEntity<ApiResponse<List<EmployerEnquiryResponse>>> enquiries() { return ResponseEntity.ok(ApiResponse.ok(service.enquiries())); }
    @PutMapping("/admin/employer-enquiries/{id}") public ResponseEntity<ApiResponse<EmployerEnquiryResponse>> updateEnquiry(@PathVariable Long id, @Valid @RequestBody com.vitc.dto.request.EnquiryStatusRequest request) { return ResponseEntity.ok(ApiResponse.ok(service.updateEnquiry(id, request))); }
    @DeleteMapping("/admin/employer-enquiries/{id}") public ResponseEntity<ApiResponse<Void>> deleteEnquiry(@PathVariable Long id) { service.deleteEnquiry(id); return ResponseEntity.ok(ApiResponse.message("Enquiry deleted")); }
}

package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.AdminAssignmentFileResponse;
import com.vitc.dto.response.AssignmentFileUploadResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.AssignmentFulfilmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin -> Assignment Upload: the ready-made project file of each catalogue assignment, sent
 * automatically to every buyer once their payment is verified.
 */
@Tag(name = "Admin - Assignment project files", description = "Upload ready-made project files for catalogue assignments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/assignment-files")
public class AdminAssignmentFileController {

    private final AssignmentFulfilmentService service;

    @Operation(summary = "List every assignment with its project file and waiting/delivered order counts")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAssignmentFileResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.listAssignmentFiles()));
    }

    @Operation(summary = "Upload or replace an assignment's project file")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping(path = "/{assignmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssignmentFileUploadResponse>> upload(
            @PathVariable Long assignmentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean sendToWaitingBuyers) {
        return ResponseEntity.ok(ApiResponse.ok("Project file uploaded",
                service.uploadAssignmentFile(assignmentId, file, sendToWaitingBuyers)));
    }

    @Operation(summary = "Remove an assignment's project file (buyers who already got it keep their links)")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<ApiResponse<AdminAssignmentFileResponse>> remove(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(ApiResponse.ok("Project file removed", service.removeAssignmentFile(assignmentId)));
    }
}

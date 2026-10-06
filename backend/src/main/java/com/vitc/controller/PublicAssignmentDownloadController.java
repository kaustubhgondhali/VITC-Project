package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.AssignmentDownloadInfoResponse;
import com.vitc.service.AssignmentFulfilmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The buyer's private download link for a delivered assignment. No session: the unguessable,
 * expiring token emailed to the buyer is the credential (only its hash is stored).
 */
@Tag(name = "Assignment downloads", description = "Buyer download of a delivered assignment project")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/assignment-downloads")
public class PublicAssignmentDownloadController {

    private final AssignmentFulfilmentService service;

    @Operation(summary = "Describe the package behind a download link")
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<AssignmentDownloadInfoResponse>> info(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(service.downloadInfo(token)));
    }

    @Operation(summary = "Download the delivered project package")
    @GetMapping("/{token}/file")
    public ResponseEntity<Resource> file(@PathVariable String token) {
        AssignmentFulfilmentService.AssignmentDownload download = service.openDownload(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(download.resource());
    }
}

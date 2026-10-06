package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.MediaFileResponse;
import com.vitc.service.FileStorageService;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Files", description = "Upload, list and delete media files (images and documents)")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService service;

    @Operation(summary = "List uploaded files, optionally filtered by type (IMAGE/DOCUMENT/OTHER) or folder")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String folder) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(type, folder)));
    }

    @Operation(summary = "Get a file record by id")
    @RequireRole(Role.MAIN_ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaFileResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Upload a single file")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaFileResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "general") String folder,
            @RequestParam(required = false) String uploadedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Uploaded successfully", service.upload(file, folder, uploadedBy)));
    }

    @Operation(summary = "Upload a Gallery image")
    @RequireRole(Role.MAIN_ADMIN)
    @PostMapping(path = "/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaFileResponse>> uploadGalleryImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String uploadedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Uploaded successfully", service.upload(file, "gallery", uploadedBy)));
    }

    @Operation(summary = "Upload multiple files")
    @PostMapping(path = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> uploadMany(
            @RequestPart("files") MultipartFile[] files,
            @RequestParam(required = false, defaultValue = "general") String folder,
            @RequestParam(required = false) String uploadedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Uploaded successfully", service.uploadMany(files, folder, uploadedBy)));
    }

    @Operation(summary = "Delete a file")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "Delete multiple files")
    @RequireRole(Role.MAIN_ADMIN)
    @DeleteMapping("/bulk")
    public ResponseEntity<ApiResponse<Void>> deleteMany(@RequestBody List<Long> ids) {
        service.deleteMany(ids);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }
}

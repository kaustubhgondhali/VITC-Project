package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.SuccessStoryRequest;
import com.vitc.dto.response.MediaFileResponse;
import com.vitc.dto.response.SuccessStoryResponse;
import com.vitc.dto.response.SuccessStoryVideoUploadResponse;
import com.vitc.security.CurrentUserContext;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.FileStorageService;
import com.vitc.service.SuccessStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * Teacher Admin management screen for video Success Stories (students/teachers/
 * parents). Lives under {@code /api/v1/teacher/**}, so it is guarded by the same
 * {@code TeacherAuthInterceptor} registration in {@code WebConfig} as every other
 * teacher endpoint — no separate security wiring needed — and additionally opts
 * into the centralized {@link RequireRole} check so a valid-but-wrong-role session
 * (Main Admin or Student) is rejected with 403, never reaching the service layer.
 *
 * <p>{@code createdBy}/{@code createdByRole} are always taken from the
 * server-resolved {@link CurrentUserContext}, never from the request body, so a
 * caller can never forge who created a story.</p>
 */
@Tag(name = "Teacher Success Stories", description = "Teacher Admin management of video success stories (create/update/delete/publish)")
@RestController
@RequestMapping("/api/v1/teacher/success-stories")
@RequiredArgsConstructor
@RequireRole(Role.TEACHER)
public class TeacherSuccessStoryController {

    private final SuccessStoryService service;
    private final FileStorageService fileStorageService;

    @Operation(summary = "List all success stories, any status (Teacher Admin view)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SuccessStoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllForAdmin()));
    }

    @Operation(summary = "List success stories for a category, any status (Teacher Admin view)")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<SuccessStoryResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByCategoryForAdmin(category)));
    }

    @Operation(summary = "Get a single success story by id, any status")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Create a success story (always starts as DRAFT)")
    @PostMapping
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> create(@Valid @RequestBody SuccessStoryRequest request) {
        String createdBy = CurrentUserContext.get() != null ? CurrentUserContext.get().identifier() : null;
        SuccessStoryResponse created = service.create(request, createdBy, Role.TEACHER.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Created successfully", created));
    }

    @Operation(summary = "Update a success story")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody SuccessStoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Delete a success story")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "Publish a story, making it visible on the public page")
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Story published", service.publish(id)));
    }

    @Operation(summary = "Unpublish a story, hiding it from the public page without deleting it")
    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> unpublish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Story unpublished", service.unpublish(id)));
    }

    /**
     * PART 3/6 — SUCCESS STORIES TEACHER ADMIN MANAGEMENT.
     *
     * <p>Uploads the video file for a success story and returns its public URL, to be sent back
     * as {@code videoUrl} on the create/update call above. Kept as its own step (rather than one
     * combined multipart create call) so the Add/Edit form can show real upload progress and so
     * an in-progress upload never risks a half-created database row. Stored separately from
     * protected lesson videos — see {@link FileStorageService#uploadSuccessStoryVideo}.</p>
     */
    @Operation(summary = "Upload a success story video file, returns its URL")
    @PostMapping(path = "/video-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SuccessStoryVideoUploadResponse>> uploadVideo(@RequestPart("file") MultipartFile file) {
        String uploadedBy = CurrentUserContext.get() != null ? CurrentUserContext.get().identifier() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Video uploaded", fileStorageService.uploadSuccessStoryVideo(file, uploadedBy)));
    }
}

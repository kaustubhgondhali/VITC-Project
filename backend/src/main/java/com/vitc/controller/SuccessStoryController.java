package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.response.SuccessStoryResponse;
import com.vitc.service.SuccessStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PART 1/6 — SUCCESS STORIES FOUNDATION, narrowed in PART 2/6 — SUCCESS STORIES
 * DATABASE &amp; BACKEND to read-only, published-only public endpoints.
 *
 * Completely unauthenticated (matches every other public content controller in
 * this codebase, e.g. {@code GalleryItemController}), so it must never expose a
 * DRAFT story or a management operation. All create/update/delete/publish
 * functionality moved to {@link TeacherSuccessStoryController}, which is locked
 * to an authenticated Teacher Admin session.
 *
 * <p>URLs are unchanged from PART 1/6 ({@code /approved}, {@code /category/{cat}})
 * so the existing {@code assets/js/success-stories.js} keeps working untouched.</p>
 */
@Tag(name = "Success Stories", description = "Public read-only endpoints for published video success stories (students/teachers/parents)")
@RestController
@RequestMapping("/api/v1/success-stories")
@RequiredArgsConstructor
public class SuccessStoryController {

    private final SuccessStoryService service;

    @Operation(summary = "Get a single published story by id (public)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SuccessStoryResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublishedById(id)));
    }

    @Operation(summary = "List all published stories (public)")
    @GetMapping("/approved")
    public ResponseEntity<ApiResponse<List<SuccessStoryResponse>>> approved() {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished()));
    }

    @Operation(summary = "List published stories for a category: student, teacher or parent (public)")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<SuccessStoryResponse>>> byCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublishedByCategory(category)));
    }
}

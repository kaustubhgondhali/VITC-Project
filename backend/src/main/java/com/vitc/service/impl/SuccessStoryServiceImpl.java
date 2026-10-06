package com.vitc.service.impl;

import com.vitc.dto.request.SuccessStoryRequest;
import com.vitc.dto.response.SuccessStoryResponse;
import com.vitc.entity.Course;
import com.vitc.entity.SuccessStory;
import com.vitc.entity.enums.SuccessStoryCategory;
import com.vitc.entity.enums.SuccessStoryStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.SuccessStoryMapper;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.SuccessStoryRepository;
import com.vitc.service.FileStorageService;
import com.vitc.service.SuccessStoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 2/6 — SUCCESS STORIES DATABASE & BACKEND.
 *
 * Public read methods only ever return {@code PUBLISHED} rows; every write/manage
 * method is called exclusively from {@code TeacherSuccessStoryController}, which is
 * itself locked to authenticated Teacher Admin sessions by {@code TeacherAuthInterceptor}
 * + {@code @RequireRole(Role.TEACHER)} — this service does not re-check the caller's
 * identity, it trusts the controller boundary the same way every other service in
 * this codebase does.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuccessStoryServiceImpl implements SuccessStoryService {

    private final SuccessStoryRepository repository;
    private final CourseRepository courseRepository;
    private final FileStorageService fileStorageService;

    /** Must match FileStorageServiceImpl's dedicated success-story video/thumbnail folders. */
    private static final String VIDEO_FOLDER = "success-story-videos";
    private static final String THUMBNAIL_FOLDER = "success-stories";

    /* ---------------- Public (published only) ---------------- */

    @Override
    public List<SuccessStoryResponse> getPublished() {
        return repository.findByStatusOrderByDisplayOrderAscCreatedAtDesc(SuccessStoryStatus.PUBLISHED).stream()
                .map(this::toResponseWithCourse).toList();
    }

    @Override
    public List<SuccessStoryResponse> getPublishedByCategory(String category) {
        String normalized = SuccessStoryCategory.fromValue(category).toDbValue();
        return repository.findByStatusAndCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtDesc(
                        SuccessStoryStatus.PUBLISHED, normalized)
                .stream().map(this::toResponseWithCourse).toList();
    }

    @Override
    public SuccessStoryResponse getPublishedById(Long id) {
        SuccessStory entity = find(id);
        if (entity.getStatus() != SuccessStoryStatus.PUBLISHED) {
            // A draft is treated as "not found" for public callers - never leaks that a
            // draft with this id exists.
            throw new ResourceNotFoundException("SuccessStory", id);
        }
        return toResponseWithCourse(entity);
    }

    /* ---------------- Teacher Admin (any status) ---------------- */

    @Override
    public List<SuccessStoryResponse> getAllForAdmin() {
        return repository.findAllByOrderByDisplayOrderAscCreatedAtDesc().stream()
                .map(this::toResponseWithCourse).toList();
    }

    @Override
    public List<SuccessStoryResponse> getByCategoryForAdmin(String category) {
        String normalized = SuccessStoryCategory.fromValue(category).toDbValue();
        return repository.findByCategoryIgnoreCaseOrderByDisplayOrderAscCreatedAtDesc(normalized).stream()
                .map(this::toResponseWithCourse).toList();
    }

    @Override
    public SuccessStoryResponse getById(Long id) {
        return toResponseWithCourse(find(id));
    }

    @Override
    @Transactional
    public SuccessStoryResponse create(SuccessStoryRequest request, String createdBy, String createdByRole) {
        SuccessStoryCategory.fromValue(request.category());
        validateCourseId(request.courseId());
        SuccessStory entity = SuccessStoryMapper.toEntity(request);
        entity.setStatus(SuccessStoryStatus.DRAFT);
        entity.setCreatedBy(createdBy);
        entity.setCreatedByRole(createdByRole);
        return toResponseWithCourse(repository.save(entity));
    }

    @Override
    @Transactional
    public SuccessStoryResponse update(Long id, SuccessStoryRequest request) {
        SuccessStoryCategory.fromValue(request.category());
        validateCourseId(request.courseId());
        SuccessStory entity = find(id);
        String oldVideoUrl = entity.getVideoUrl();
        String oldThumbnailUrl = entity.getThumbnailUrl();
        SuccessStoryMapper.apply(entity, request);
        SuccessStoryResponse response = toResponseWithCourse(repository.save(entity));
        if (!java.util.Objects.equals(oldVideoUrl, entity.getVideoUrl())) {
            fileStorageService.deleteFileIfManaged(oldVideoUrl, VIDEO_FOLDER);
        }
        if (!java.util.Objects.equals(oldThumbnailUrl, entity.getThumbnailUrl())) {
            fileStorageService.deleteFileIfManaged(oldThumbnailUrl, THUMBNAIL_FOLDER);
        }
        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SuccessStory entity = find(id);
        repository.delete(entity);
        // Best-effort, folder-scoped cleanup (see FileStorageService#deleteFileIfManaged javadoc) -
        // only ever touches a file that was actually uploaded through this feature's own folders;
        // an external URL (e.g. a pasted YouTube link) is left completely untouched, and nothing
        // outside success-story-videos/ or success-stories/ can ever be deleted from here.
        fileStorageService.deleteFileIfManaged(entity.getVideoUrl(), VIDEO_FOLDER);
        fileStorageService.deleteFileIfManaged(entity.getThumbnailUrl(), THUMBNAIL_FOLDER);
    }

    @Override
    @Transactional
    public SuccessStoryResponse publish(Long id) {
        SuccessStory entity = find(id);
        entity.setStatus(SuccessStoryStatus.PUBLISHED);
        return toResponseWithCourse(repository.save(entity));
    }

    @Override
    @Transactional
    public SuccessStoryResponse unpublish(Long id) {
        SuccessStory entity = find(id);
        entity.setStatus(SuccessStoryStatus.DRAFT);
        return toResponseWithCourse(repository.save(entity));
    }

    /* ---------------- helpers ---------------- */

    private SuccessStory find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SuccessStory", id));
    }

    private void validateCourseId(Long courseId) {
        if (courseId != null && !courseRepository.existsById(courseId)) {
            throw new BadRequestException("No course found with id: " + courseId);
        }
    }

    private SuccessStoryResponse toResponseWithCourse(SuccessStory entity) {
        String courseTitle = null;
        if (entity.getCourseId() != null) {
            courseTitle = courseRepository.findById(entity.getCourseId()).map(Course::getTitle).orElse(null);
        }
        return SuccessStoryMapper.toResponse(entity, courseTitle);
    }
}

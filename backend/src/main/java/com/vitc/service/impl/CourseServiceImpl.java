package com.vitc.service.impl;

import com.vitc.dto.request.CourseRequest;
import com.vitc.dto.response.CourseResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.mapper.CourseMapper;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.service.AuditLogService;
import com.vitc.service.CourseService;
import com.vitc.service.FileStorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private final CourseRepository repository;

    /*
     * FIX COURSE DELETE - the four repositories below mirror exactly what
     * AdminCourseContentServiceImpl#deleteModule / #deleteLesson already use
     * to clean up course-owned content safely (no schema change, no new
     * cascade annotations - the same repository methods, reused here so a
     * course delete removes its own curriculum instead of being blocked by
     * the FK from course_modules.course_id / course_lessons.module_id).
     *
     * EnrollmentRepository is the safety check: enrollments.course_id is a
     * real, NOT NULL foreign key (see database/vitc_db_fresh.sql) that
     * anchors a student's order/payment/progress trail to the course. Its
     * presence is what makes a course "has historical/business data" - those
     * rows are never deleted here. When any exist, the course is left alone
     * and the Main Admin is told to use the existing Hide action instead.
     */
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Override
    public List<CourseResponse> getAll() {
        return repository.findAll().stream().map(CourseMapper::toResponse).toList();
    }

    @Override
    public CourseResponse getById(Long id) {
        return CourseMapper.toResponse(find(id));
    }

    @Override
    @Transactional
    public CourseResponse create(CourseRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Course already exists with code: " + request.code());
        }
        Course entity = CourseMapper.toEntity(request);
        return CourseMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public CourseResponse update(Long id, CourseRequest request) {
        Course entity = find(id);
        if (!entity.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Course already exists with code: " + request.code());
        }
        CourseMapper.apply(entity, request);
        return CourseMapper.toResponse(repository.save(entity));
    }

    /**
     * FIX COURSE DELETE.
     *
     * <p>Step 1 - protect historical/business data: if any enrolment
     * references this course (order/payment/progress trail), the delete is
     * refused outright. Nothing is touched. The Main Admin is pointed at the
     * existing Hide action, which already exists via {@link #update} and is
     * left completely unchanged by this method.</p>
     *
     * <p>Step 2 - when safe, clean up course-owned content only, in
     * dependency order, using the same repository calls
     * {@code AdminCourseContentServiceImpl} already uses for a single module
     * delete: lesson progress, then lessons (plus their stored video/audio
     * files), then modules, then the course itself. All in one transaction -
     * if anything fails, Spring rolls back the whole thing and the course,
     * its modules and its lessons are left exactly as they were.</p>
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Course course = find(id);

        long enrollmentCount = enrollmentRepository.countByCourseId(id);
        if (enrollmentCount > 0) {
            throw new BadRequestException(
                    "\"" + course.getTitle() + "\" cannot be permanently deleted because " + enrollmentCount
                            + " student enrollment(s) with order/payment history are linked to it. "
                            + "Use Hide to remove it from the public site instead; permanent deletion is only "
                            + "available for courses with no enrollment history.");
        }

        List<CourseModule> modules = moduleRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(id);
        for (CourseModule module : modules) {
            List<CourseLesson> lessons =
                    lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(module.getId());
            // Remove dependent rows first so no foreign key is orphaned.
            progressRepository.deleteByLessonModuleId(module.getId());
            lessonRepository.deleteAll(lessons);
            moduleRepository.delete(module);
            lessons.forEach(this::cleanupLessonMedia);
        }

        repository.delete(course);
        auditLogService.log("COURSE_DELETED", "Course", String.valueOf(id),
                "Permanently deleted course \"" + course.getTitle() + "\" and its "
                        + modules.size() + " module(s)");
    }

    private void cleanupLessonMedia(CourseLesson lesson) {
        if (lesson.getVideoUrl() != null) {
            fileStorageService.deleteVideoIfManaged(lesson.getVideoUrl());
        }
        if (lesson.getAudioUrl() != null) {
            fileStorageService.deleteFileIfManaged(lesson.getAudioUrl(), "audio");
        }
    }

    @Override
    public List<CourseResponse> getActive() {
        return repository.findByActiveTrue().stream().map(CourseMapper::toResponse).toList();
    }

    @Override
    public CourseResponse getByCode(String code) {
        return repository.findByCode(code).map(CourseMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with code: " + code));
    }

    @Override
    public List<CourseResponse> getByCategory(String category) {
        return repository.findByCategoryIgnoreCase(category).stream().map(CourseMapper::toResponse).toList();
    }

    private Course find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }
}

package com.vitc.service.impl;

import com.vitc.dto.request.AdminLessonRequest;
import com.vitc.dto.request.AdminModuleRequest;
import com.vitc.dto.response.AdminCourseContentResponse;
import com.vitc.dto.response.AdminLessonResponse;
import com.vitc.dto.response.AdminModuleResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.service.AdminCourseContentService;
import com.vitc.service.AuditLogService;
import com.vitc.service.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin -> Course Content. Writes to the existing {@code course_modules} /
 * {@code course_lessons} tables the student portal already reads, so every
 * change is immediately visible to enrolled students (and only to them - the
 * student side still checks the enrolment on every request).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCourseContentServiceImpl implements AdminCourseContentService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    @Override
    public AdminCourseContentResponse content(Long courseId) {
        Course course = course(courseId);
        List<AdminModuleResponse> modules = new ArrayList<>();
        int lessonCount = 0;
        for (CourseModule m : moduleRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId)) {
            List<AdminLessonResponse> lessons = new ArrayList<>();
            for (CourseLesson l : lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(m.getId())) {
                lessons.add(toLesson(l));
            }
            lessonCount += lessons.size();
            modules.add(toModule(m, lessons));
        }
        return new AdminCourseContentResponse(
                course.getId(), course.getCode(), course.getTitle(), modules.size(), lessonCount, modules);
    }

    /* ========================= modules ========================= */

    @Override
    @Transactional
    public AdminModuleResponse createModule(Long courseId, AdminModuleRequest request) {
        Course course = course(courseId);
        int order = request.displayOrder() != null
                ? request.displayOrder()
                : (int) moduleRepository.countByCourseId(courseId) + 1;
        CourseModule module = CourseModule.builder()
                .course(course)
                .title(request.title().trim())
                .description(request.description())
                .displayOrder(order)
                .active(request.active() == null || request.active())
                .build();
        moduleRepository.save(module);
        auditLogService.log("MODULE_CREATED", "CourseModule", String.valueOf(module.getId()),
            "Created module for course " + courseId);
        return toModule(module, lessonsOf(module.getId()));
    }

    @Override
    @Transactional
    public AdminModuleResponse updateModule(Long moduleId, AdminModuleRequest request) {
        CourseModule module = module(moduleId);
        Boolean previousActive = module.getActive();
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        if (request.displayOrder() != null) {
            module.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            module.setActive(request.active());
        }
        moduleRepository.save(module);
        auditLogService.log("MODULE_EDITED", "CourseModule", String.valueOf(moduleId), "Edited course module");
        if (!java.util.Objects.equals(previousActive, module.getActive())) {
            auditLogService.log(Boolean.TRUE.equals(module.getActive()) ? "MODULE_ACTIVATED" : "MODULE_DEACTIVATED",
                "CourseModule", String.valueOf(moduleId), "Changed course module availability");
        }
        return toModule(module, lessonsOf(moduleId));
    }

    @Override
    @Transactional
    public void deleteModule(Long moduleId) {
        CourseModule module = module(moduleId);
        List<CourseLesson> lessons = lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(moduleId);
        // Remove dependent rows first so no foreign key is orphaned.
        progressRepository.deleteByLessonModuleId(moduleId);
        lessonRepository.deleteAll(lessons);
        moduleRepository.delete(module);
        auditLogService.log("MODULE_DELETED", "CourseModule", String.valueOf(moduleId), "Deleted course module");
        lessons.forEach(this::cleanupLessonMedia);
    }

    @Override
    @Transactional
    public AdminModuleResponse moveModule(Long moduleId, int direction) {
        CourseModule module = module(moduleId);
        List<CourseModule> siblings =
                moduleRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(module.getCourse().getId());
        swap(siblings, moduleId, direction,
                CourseModule::getId, CourseModule::getDisplayOrder, CourseModule::setDisplayOrder);
        moduleRepository.saveAll(siblings);
        return toModule(module, lessonsOf(moduleId));
    }

    /* ========================= lessons ========================= */

    @Override
    @Transactional
    public AdminLessonResponse createLesson(Long moduleId, AdminLessonRequest request) {
        CourseModule module = module(moduleId);
        int order = request.displayOrder() != null
                ? request.displayOrder()
                : lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(moduleId).size() + 1;
        CourseLesson lesson = CourseLesson.builder()
                .module(module)
                .title(request.title().trim())
                .description(request.description())
                .videoUrl(request.videoUrl())
                .duration(request.duration())
                .displayOrder(order)
                .active(request.active() == null || request.active())
                .build();
        lessonRepository.save(lesson);
        return toLesson(lesson);
    }

    @Override
    @Transactional
    public AdminLessonResponse updateLesson(Long lessonId, AdminLessonRequest request) {
        CourseLesson lesson = lesson(lessonId);
        Boolean previousActive = lesson.getActive();
        lesson.setTitle(request.title().trim());
        lesson.setDescription(request.description());
        lesson.setVideoUrl(request.videoUrl());
        lesson.setDuration(request.duration());
        if (request.displayOrder() != null) {
            lesson.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            lesson.setActive(request.active());
        }
        lessonRepository.save(lesson);
        auditLogService.log("LESSON_EDITED", "CourseLesson", String.valueOf(lessonId), "Edited course lesson");
        if (!java.util.Objects.equals(previousActive, lesson.getActive())) {
            auditLogService.log(Boolean.TRUE.equals(lesson.getActive()) ? "LESSON_ACTIVATED" : "LESSON_DEACTIVATED",
                "CourseLesson", String.valueOf(lessonId), "Changed lesson availability");
        }
        return toLesson(lesson);
    }

    @Override
    @Transactional
    public void deleteLesson(Long lessonId) {
        CourseLesson lesson = lesson(lessonId);
        Long moduleId = lesson.getModule().getId();
        progressRepository.deleteByLessonId(lessonId);
        lessonRepository.delete(lesson);
        cleanupLessonMedia(lesson);
        List<CourseLesson> remaining =
                lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(moduleId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i + 1);
        }
        lessonRepository.saveAll(remaining);
        auditLogService.log("LESSON_DELETED", "CourseLesson", String.valueOf(lessonId),
                "Deleted lesson and resequenced remaining module lessons");
    }

    @Override
    @Transactional
    public AdminLessonResponse moveLesson(Long lessonId, int direction) {
        CourseLesson lesson = lesson(lessonId);
        List<CourseLesson> siblings =
                lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(lesson.getModule().getId());
        swap(siblings, lessonId, direction,
                CourseLesson::getId, CourseLesson::getDisplayOrder, CourseLesson::setDisplayOrder);
        lessonRepository.saveAll(siblings);
        return toLesson(lesson);
    }

    @Override
    @Transactional
    public AdminLessonResponse setLessonAudio(Long lessonId, MultipartFile file) {
        CourseLesson lesson = lesson(lessonId);
        String old = lesson.getAudioUrl();
        lesson.setAudioUrl(fileStorageService.uploadAudio(file, "admin").url());
        lessonRepository.save(lesson);
        auditLogService.log(old == null ? "MEDIA_UPLOADED" : "MEDIA_REPLACED", "CourseLesson",
            String.valueOf(lessonId), "Audio media saved for lesson");
        if (old != null) {
            fileStorageService.deleteFileIfManaged(old, "audio");
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lessonId),
                "Replaced previous audio media");
        }
        return toLesson(lesson);
    }

    @Override
    @Transactional
    public AdminLessonResponse clearLessonAudio(Long lessonId) {
        CourseLesson lesson = lesson(lessonId);
        String old = lesson.getAudioUrl();
        lesson.setAudioUrl(null);
        lessonRepository.save(lesson);
        if (old != null) {
            fileStorageService.deleteFileIfManaged(old, "audio");
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lessonId),
                "Deleted audio media from lesson");
        }
        return toLesson(lesson);
    }

    /* ========================= helpers ========================= */

    private <T> void swap(List<T> ordered,
                          Long id,
                          int direction,
                          java.util.function.Function<T, Long> idOf,
                          java.util.function.Function<T, Integer> orderOf,
                          java.util.function.BiConsumer<T, Integer> setOrder) {
        // Normalise to 1..n first so rows created with duplicate orders still move.
        for (int i = 0; i < ordered.size(); i++) {
            setOrder.accept(ordered.get(i), i + 1);
        }
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (idOf.apply(ordered.get(i)).equals(id)) {
                index = i;
                break;
            }
        }
        int target = index + (direction < 0 ? -1 : 1);
        if (index < 0 || target < 0 || target >= ordered.size()) {
            return;
        }
        T a = ordered.get(index);
        T b = ordered.get(target);
        int ao = orderOf.apply(a);
        setOrder.accept(a, orderOf.apply(b));
        setOrder.accept(b, ao);
    }

    private List<AdminLessonResponse> lessonsOf(Long moduleId) {
        List<AdminLessonResponse> out = new ArrayList<>();
        for (CourseLesson l : lessonRepository.findByModuleIdOrderByDisplayOrderAscIdAsc(moduleId)) {
            out.add(toLesson(l));
        }
        return out;
    }

    private static AdminModuleResponse toModule(CourseModule m, List<AdminLessonResponse> lessons) {
        return new AdminModuleResponse(
                m.getId(), m.getCourse().getId(), m.getTitle(), m.getDescription(),
                m.getDisplayOrder(), m.getActive(), lessons);
    }

    private static AdminLessonResponse toLesson(CourseLesson l) {
        return new AdminLessonResponse(
                l.getId(), l.getModule().getId(), l.getTitle(), l.getDescription(),
                l.getVideoUrl(), l.getAudioUrl(), l.getDuration(), l.getDisplayOrder(), l.getActive());
    }

    private void cleanupLessonMedia(CourseLesson lesson) {
        if (lesson.getVideoUrl() != null) {
            fileStorageService.deleteVideoIfManaged(lesson.getVideoUrl());
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lesson.getId()),
                    "Deleted video media with lesson");
        }
        if (lesson.getAudioUrl() != null) {
            fileStorageService.deleteFileIfManaged(lesson.getAudioUrl(), "audio");
            auditLogService.log("MEDIA_DELETED", "CourseLesson", String.valueOf(lesson.getId()),
                    "Deleted audio media with lesson");
        }
    }

    private Course course(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
    }

    private CourseModule module(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
    }

    private CourseLesson lesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));
    }
}

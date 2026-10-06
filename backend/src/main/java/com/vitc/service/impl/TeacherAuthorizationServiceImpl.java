package com.vitc.service.impl;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.ForbiddenException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.TeacherAuthorizationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 10A - the one place Teacher authorization is decided.
 *
 * <p>Deliberately paranoid: even though {@code TeacherAuthInterceptor} already validated the
 * session, the role and account status are re-checked here, so a Teacher API can never be reached
 * by a non-teacher account (defence in depth, and it keeps service-level calls safe too).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherAuthorizationServiceImpl implements TeacherAuthorizationService {

    private static final String NOT_YOURS = "You are not authorised to access this course content";

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;

    @Override
    public User requireTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new ForbiddenException("Teacher authorisation required");
        }
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new ForbiddenException("Teacher authorisation required"));
        if (user.getRole() != UserRole.TEACHER || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Teacher authorisation required");
        }
        return user;
    }

    @Override
    public Course requireAssignedCourse(Long teacherId, Long courseId) {
        requireTeacher(teacherId);
        // A non-existent course id is a 404 (no such resource); a course that exists but belongs
        // to another teacher is a 403 (Forbidden). Neither branch reveals who the course actually
        // belongs to - only "not found" vs "not yours".
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        if (course.getTeacherId() == null || !course.getTeacherId().equals(teacherId)) {
            throw new ForbiddenException(NOT_YOURS);
        }
        return course;
    }

    @Override
    public CourseModule requireOwnedModule(Long teacherId, Long moduleId) {
        requireTeacher(teacherId);
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
        Long courseId = module.getCourse() != null ? module.getCourse().getId() : null;
        if (courseId == null) {
            throw new ForbiddenException(NOT_YOURS);
        }
        requireAssignedCourse(teacherId, courseId);
        return module;
    }

    @Override
    public CourseLesson requireOwnedLesson(Long teacherId, Long lessonId) {
        requireTeacher(teacherId);
        CourseLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));
        Long moduleId = lesson.getModule() != null ? lesson.getModule().getId() : null;
        if (moduleId == null) {
            throw new ForbiddenException(NOT_YOURS);
        }
        requireOwnedModule(teacherId, moduleId);
        return lesson;
    }

    @Override
    public List<Course> assignedCourses(Long teacherId) {
        requireTeacher(teacherId);
        return courseRepository.findByTeacherId(teacherId);
    }
}

package com.vitc.service.impl;

import com.vitc.dto.request.CourseRequest;
import com.vitc.dto.request.TeacherCourseInfoRequest;
import com.vitc.dto.response.CourseResponse;
import com.vitc.entity.Course;
import com.vitc.mapper.CourseMapper;
import com.vitc.service.CourseService;
import com.vitc.service.TeacherAuthorizationService;
import com.vitc.service.TeacherCourseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reuses {@link CourseRepository}/{@link Course}/{@link CourseMapper} - the exact same course
 * architecture Main Admin and Students already use. No second course-content model is created for
 * the Teacher Dashboard.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherCourseServiceImpl implements TeacherCourseService {

    /** PART 10A: central Teacher authorization authority. */
    private final TeacherAuthorizationService authorization;
    /** PART 3/8: the existing Main Admin course write path - reused, not duplicated. */
    private final CourseService courseService;

    @Override
    public List<CourseResponse> myCourses(Long teacherId) {
        return authorization.assignedCourses(teacherId).stream()
                .map(CourseMapper::toResponse)
                .toList();
    }

    @Override
    public CourseResponse myCourseById(Long teacherId, Long courseId) {
        // Backend-enforced (PART 10A): authenticated -> TEACHER -> course assigned to this teacher.
        // A teacher hand-editing the id in the URL or calling the API directly gets 403.
        return CourseMapper.toResponse(authorization.requireAssignedCourse(teacherId, courseId));
    }

    @Override
    @Transactional
    public CourseResponse updateCourseInfo(Long teacherId, Long courseId, TeacherCourseInfoRequest request) {
        // Same ownership gate as every other Teacher content write: not-found vs not-yours is
        // decided here, before a single field is touched.
        Course course = authorization.requireAssignedCourse(teacherId, courseId);
        // Reuse the existing CourseService.update(...) so there is exactly one place course rows
        // are written; every field the Teacher must not touch (code, price, category, active, ...)
        // is carried over unchanged from the current row.
        CourseRequest merged = new CourseRequest(
                course.getCode(),
                request.title().trim(),
                request.description(),
                course.getPrice(),
                course.getMeta(),
                course.getLevel(),
                course.getCategory(),
                course.getDurationMonths(),
                course.getIcon(),
                course.getActive());
        return courseService.update(courseId, merged);
    }
}

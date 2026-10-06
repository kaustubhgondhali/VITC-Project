package com.vitc.service;

import com.vitc.dto.request.TeacherCourseInfoRequest;
import com.vitc.dto.response.CourseResponse;
import java.util.List;

/**
 * Teacher Admin -> "My Courses" (PART 8B). Deliberately thin: it reuses the
 * existing {@code courses} table/entity and {@link CourseResponse} DTO -
 * there is no separate Teacher course model. Every method here MUST scope
 * its result to the given teacher's own assigned courses only, exactly like
 * {@link TeacherDashboardService}.
 */
public interface TeacherCourseService {

    /** Courses assigned to the given teacher (Course.teacherId = teacherId) - never the full catalogue. */
    List<CourseResponse> myCourses(Long teacherId);

    /**
     * A single assigned course, for the "Manage Content" screen.
     * Throws {@link com.vitc.exception.ResourceNotFoundException} if the course does not exist,
     * and {@link com.vitc.exception.ForbiddenException} if it exists but is not assigned to this
     * teacher - so a teacher can never reach another teacher's (or an unassigned) course by
     * guessing/editing the id in the URL.
     */
    CourseResponse myCourseById(Long teacherId, Long courseId);

    /**
     * PART 3/8 - minimum compatible course editing: a Teacher may update only the title and
     * description of one of their own assigned courses. Reuses the existing {@code CourseService}
     * write path (same {@code courses} table/entity/repository) - no new course model. Throws
     * {@link com.vitc.exception.ResourceNotFoundException} / {@link com.vitc.exception.ForbiddenException}
     * with the same "not found" vs "not yours" semantics as every other Teacher content endpoint.
     */
    CourseResponse updateCourseInfo(Long teacherId, Long courseId, TeacherCourseInfoRequest request);
}

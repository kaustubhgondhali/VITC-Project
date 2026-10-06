package com.vitc.service.impl;

import com.vitc.dto.response.TeacherDashboardStatsResponse;
import com.vitc.entity.Course;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.service.TeacherAuthorizationService;
import com.vitc.service.TeacherDashboardService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes Teacher Dashboard statistics strictly from the courses assigned
 * to the requesting teacher ({@code Course.teacherId}). A teacher with no
 * assigned courses correctly sees all zeros - this service never falls back
 * to the full course catalogue.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherDashboardServiceImpl implements TeacherDashboardService {

    private final CourseRepository courseRepository;
    /** PART 10A: central Teacher authorization authority (role + assignment check). */
    private final TeacherAuthorizationService authorization;
    private final CourseModuleRepository courseModuleRepository;
    private final CourseLessonRepository courseLessonRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Override
    public TeacherDashboardStatsResponse dashboardStats(Long teacherId) {
        List<Course> myCourses = authorization.assignedCourses(teacherId);

        if (myCourses.isEmpty()) {
            return new TeacherDashboardStatsResponse(0, 0, 0, 0, 0, 0, 0, 0);
        }

        List<Long> courseIds = myCourses.stream().map(Course::getId).collect(Collectors.toList());

        long totalModules = courseModuleRepository.countByCourseIdIn(courseIds);
        long totalLessons = courseLessonRepository.countByCourseIds(courseIds);
        long totalVideos = courseLessonRepository.countVideosByCourseIds(courseIds);
        long activeStudents = enrollmentRepository.countDistinctStudentsByCourseIdsAndStatus(
                courseIds, EnrollmentStatus.ACTIVE);

        /* PART 1/8 (Phase 2): a course with a null `active` flag defaults to published,
           matching Course entity's own default (active = true). */
        long publishedCourses = myCourses.stream()
                .filter(c -> c.getActive() == null || c.getActive())
                .count();
        long draftCourses = myCourses.size() - publishedCourses;
        long totalEnrolledStudents = enrollmentRepository.countDistinctStudentsByCourseIds(courseIds);

        return new TeacherDashboardStatsResponse(
                myCourses.size(), totalModules, totalLessons, totalVideos, activeStudents,
                publishedCourses, draftCourses, totalEnrolledStudents);
    }
}

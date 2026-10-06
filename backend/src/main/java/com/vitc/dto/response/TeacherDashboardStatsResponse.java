package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated counters for the Teacher Admin dashboard. Every number here is
 * computed only from the courses assigned to the requesting teacher
 * (never from the whole catalogue) - see {@code TeacherDashboardServiceImpl}.
 */
@Schema(name = "TeacherDashboardStatsResponse", description = "Aggregated counters for the Teacher Admin dashboard, scoped to the logged-in teacher's own courses")
public record TeacherDashboardStatsResponse(
    long myCourses,
    long totalModules,
    long totalLessons,
    long totalVideos,
    long activeStudents,
    long publishedCourses,
    long draftCourses,
    long totalEnrolledStudents
) {
}

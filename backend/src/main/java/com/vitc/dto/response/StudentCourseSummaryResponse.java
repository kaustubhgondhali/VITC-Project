package com.vitc.dto.response;

import com.vitc.entity.enums.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** "My Courses" card: enrolment + computed learning progress. */
@Schema(name = "StudentCourseSummaryResponse", description = "Enrolled course with progress for the My Courses list")
public record StudentCourseSummaryResponse(
        Long enrollmentId,
        Long courseId,
        String courseCode,
        String courseTitle,
        String courseDescription,
        String icon,
        String instructorName,
        String level,
        String category,
        String durationMonths,
        EnrollmentStatus status,
        boolean accessAllowed,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        Long resumeLessonId,
        String resumeLessonTitle,
        Long lastAccessedLessonId,
        String lastAccessedLessonTitle,
        LocalDateTime lastWatchedAt,
        LocalDateTime enrolledAt) {
}

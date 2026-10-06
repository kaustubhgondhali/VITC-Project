package com.vitc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.vitc.entity.enums.EnrollmentStatus;
import java.time.LocalDateTime;

/**
 * PART 4/8 - one enrolled student's progress in one of the logged-in Teacher's own assigned
 * courses, for the dedicated "Students" page (which lists every student across every course the
 * teacher is assigned to, not just one course at a time).
 *
 * <p>Identical in spirit to {@link TeacherStudentProgressResponse} - same allowed fields, same
 * "never expose password/token/auth data" rule - plus the course each row belongs to, since a
 * single teacher can have many courses and a single student can appear once per course they are
 * enrolled in.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeacherStudentRosterResponse(
        Long studentId,
        String studentLoginId,
        String fullName,
        String email,
        Long courseId,
        String courseTitle,
        EnrollmentStatus enrollmentStatus,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        LocalDateTime lastWatchedAt,
        LocalDateTime enrolledAt) {
}

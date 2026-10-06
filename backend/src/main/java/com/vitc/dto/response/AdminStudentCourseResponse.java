package com.vitc.dto.response;

import com.vitc.entity.enums.EnrollmentStatus;
import java.time.LocalDateTime;

/** One enrolment of a student, with the admin-visible progress summary. */
public record AdminStudentCourseResponse(
        Long enrollmentId,
        Long courseId,
        String courseCode,
        String courseTitle,
        EnrollmentStatus status,
        boolean accessGranted,
        /** true when an admin granted access without a payment record. */
        boolean manuallyGranted,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        LocalDateTime lastWatchedAt,
        LocalDateTime enrolledAt) {
}

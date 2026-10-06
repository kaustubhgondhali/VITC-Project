package com.vitc.dto.response;

import com.vitc.entity.enums.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** A course the authenticated student is enrolled in. */
@Schema(name = "StudentCourseResponse", description = "Course enrolment owned by the authenticated student")
public record StudentCourseResponse(
        Long enrollmentId,
        Long courseId,
        String courseCode,
        String courseTitle,
        String level,
        String category,
        String durationMonths,
        EnrollmentStatus status,
        LocalDateTime enrolledAt) {
}

package com.vitc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.vitc.entity.enums.EnrollmentStatus;
import java.time.LocalDateTime;

/**
 * PART 11B-3 - one enrolled student's progress within a single course, as seen by the Teacher
 * assigned to that course. Never includes another course's data and never includes the student's
 * password hash, session token, or any field the student portal itself does not already expose
 * about the student's own progress.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeacherStudentProgressResponse(
        Long studentId,
        String studentLoginId,
        String fullName,
        String email,
        EnrollmentStatus enrollmentStatus,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        LocalDateTime lastWatchedAt,
        LocalDateTime enrolledAt) {
}

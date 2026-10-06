package com.vitc.dto.response;

import com.vitc.entity.enums.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "StudentCourseDetailResponse", description = "Course the student owns, with modules and lessons")
public record StudentCourseDetailResponse(
        Long courseId,
        String courseCode,
        String courseTitle,
        String description,
        String level,
        String category,
        String durationMonths,
        EnrollmentStatus enrollmentStatus,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        Long resumeLessonId,
        List<StudentModuleResponse> modules) {
}

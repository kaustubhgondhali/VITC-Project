package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authorised lesson payload. Returned only after the backend verified an
 * active enrolment for the owning course, so it is the single place where a
 * protected {@code videoUrl} is exposed.
 */
@Schema(name = "StudentLessonDetailResponse", description = "Authorised lesson with playable video")
public record StudentLessonDetailResponse(
        Long id,
        Long moduleId,
        String moduleTitle,
        Long courseId,
        String courseTitle,
        String title,
        String description,
        String duration,
        String videoUrl,
        String videoType,
        String audioUrl,
        boolean completed,
        int progressPercentage,
        Long previousLessonId,
        Long nextLessonId,
        int lessonNumber,
        int totalLessons,
        int courseProgressPercentage) {
}

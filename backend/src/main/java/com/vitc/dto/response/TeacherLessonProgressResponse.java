package com.vitc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * PART 11B-3 - per-lesson completion detail for one student's progress within a single course,
 * as seen by the Teacher assigned to that course.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeacherLessonProgressResponse(
        Long lessonId,
        String lessonTitle,
        Long moduleId,
        String moduleTitle,
        boolean completed,
        LocalDateTime lastWatchedAt) {
}

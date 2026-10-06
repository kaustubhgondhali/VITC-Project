package com.vitc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "StudentModuleResponse", description = "Course module with its lessons")
public record StudentModuleResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer displayOrder,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        List<StudentLessonResponse> lessons) {
}

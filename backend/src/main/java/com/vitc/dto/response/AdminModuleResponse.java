package com.vitc.dto.response;

import java.util.List;

/** Admin view of a module and its lessons (active and inactive alike). */
public record AdminModuleResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer displayOrder,
        Boolean active,
        List<AdminLessonResponse> lessons) {
}

package com.vitc.dto.response;

import java.util.List;

/** Full content tree of one course for the admin Course Content screen. */
public record AdminCourseContentResponse(
        Long courseId,
        String courseCode,
        String courseTitle,
        int moduleCount,
        int lessonCount,
        List<AdminModuleResponse> modules) {
}

package com.vitc.dto.response;

/** PART 10C - one course row in the Teachers screen (assigned or assignable). */
public record AdminTeacherCourseResponse(
        Long id,
        String code,
        String title,
        String category,
        Boolean active,
        Long assignedTeacherId,
        String assignedTeacherName) {
}

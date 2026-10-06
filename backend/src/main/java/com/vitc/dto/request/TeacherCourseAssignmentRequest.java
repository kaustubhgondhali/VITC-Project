package com.vitc.dto.request;

import java.util.List;

/**
 * PART 10C - the complete set of courses that must be assigned to a Teacher.
 * Courses missing from the list are un-assigned (authorisation removed);
 * their modules, lessons and every other historical record are preserved.
 */
public record TeacherCourseAssignmentRequest(List<Long> courseIds) {
}

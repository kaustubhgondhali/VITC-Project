package com.vitc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PART 3/8 (Course Content Management Integration) - the minimum compatible course-edit surface
 * for a Teacher.
 *
 * <p>Deliberately narrow: a Teacher may correct their own course's title/description (content
 * they are responsible for), but never its code, price, category or active flag - those remain
 * Main-Admin-only business fields, unchanged from the existing {@code CourseRequest}/{@code
 * CourseService} used by {@code admin/courses.html}. No new course entity, table or repository is
 * introduced; the existing {@code CourseService.update(...)} still does the actual write.</p>
 */
public record TeacherCourseInfoRequest(
        @NotBlank(message = "Title is required") @Size(max = 150) String title,
        @Size(max = 2000) String description) {
}

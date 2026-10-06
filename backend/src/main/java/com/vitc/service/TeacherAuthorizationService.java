package com.vitc.service;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import java.util.List;

/**
 * PART 10A - the single, central authority for Teacher backend authorization.
 *
 * <p>Every Teacher API must run the full chain before it reads or writes anything:</p>
 * <ol>
 *   <li>the caller is authenticated (session resolved by {@code TeacherAuthInterceptor});</li>
 *   <li>the caller's role is {@code TEACHER} and the account is {@code ACTIVE};</li>
 *   <li>the target course is assigned to that teacher ({@code Course.teacherId});</li>
 *   <li>for nested resources: module -&gt; course and lesson -&gt; module -&gt; course resolve to an
 *       assigned course.</li>
 * </ol>
 *
 * <p>Any failure throws {@link com.vitc.exception.ForbiddenException} -&gt; HTTP 403. Ids arriving
 * from the browser (path variables, query params, request bodies) are always untrusted: they are
 * re-loaded from the database and re-checked here, so hand-edited URLs, tampered ids or requests
 * sent directly with curl are rejected exactly like a hidden button would be.</p>
 *
 * <p>This does NOT introduce a second authentication system: it consumes the teacher id that the
 * existing {@code TeacherAuthInterceptor} session check publishes, and reuses the existing
 * {@code users} / {@code courses} / {@code course_modules} / {@code course_lessons} model.</p>
 */
public interface TeacherAuthorizationService {

    /** Re-verifies (server-side) that the session teacher still exists, is ACTIVE and is a TEACHER. */
    User requireTeacher(Long teacherId);

    /** Requirement 1 + 3: authenticated TEACHER assigned to this course, else 403. */
    Course requireAssignedCourse(Long teacherId, Long courseId);

    /** Module -&gt; course -&gt; teacher. 403 when the module belongs to another teacher's course. */
    CourseModule requireOwnedModule(Long teacherId, Long moduleId);

    /** Lesson -&gt; module -&gt; course -&gt; teacher. 403 when it is not the teacher's content. */
    CourseLesson requireOwnedLesson(Long teacherId, Long lessonId);

    /** The courses assigned to this teacher - the only courses any Teacher API may ever touch. */
    List<Course> assignedCourses(Long teacherId);
}

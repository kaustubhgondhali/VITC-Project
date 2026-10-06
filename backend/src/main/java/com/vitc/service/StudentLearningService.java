package com.vitc.service;

import com.vitc.dto.request.LessonProgressRequest;
import com.vitc.dto.response.StudentCourseDetailResponse;
import com.vitc.dto.response.StudentCourseSummaryResponse;
import com.vitc.dto.response.StudentLessonDetailResponse;
import com.vitc.dto.response.StudentModuleResponse;
import java.util.List;

/**
 * Student learning portal (My Courses, modules, lessons, progress).
 *
 * <p>Every method takes the {@code studentId} resolved from the server-side
 * session by {@code StudentAuthInterceptor} and verifies the enrolment itself,
 * so changing a course/lesson id in the URL can never leak another course or
 * another student's data - it raises {@code ForbiddenException} (HTTP 403).</p>
 */
public interface StudentLearningService {

    List<StudentCourseSummaryResponse> myCourses(Long studentId);

    StudentCourseDetailResponse course(Long studentId, Long courseId);

    List<StudentModuleResponse> modules(Long studentId, Long courseId);

    StudentLessonDetailResponse lesson(Long studentId, Long lessonId);

    StudentLessonDetailResponse saveProgress(Long studentId, Long lessonId, LessonProgressRequest request);
}

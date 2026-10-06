package com.vitc.service.impl;

import com.vitc.dto.response.TeacherLessonProgressResponse;
import com.vitc.dto.response.TeacherStudentProgressResponse;
import com.vitc.dto.response.TeacherStudentRosterResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.Enrollment;
import com.vitc.entity.StudentLessonProgress;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.exception.ForbiddenException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.service.TeacherAuthorizationService;
import com.vitc.service.TeacherStudentProgressService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 11B-3 - Teacher view of student progress, built on top of the same {@code enrollments} /
 * {@code student_lesson_progress} tables the Admin and Student portals already use. No duplicate
 * progress model is introduced.
 *
 * <p>Authorization chain for every method: authenticated Teacher -&gt; course assigned to that
 * Teacher (via {@link TeacherAuthorizationService#requireAssignedCourse}) -&gt; for the
 * single-student view, the requested student must actually be enrolled in that exact course. A
 * course id from another teacher, or a student id that was never enrolled in the given course,
 * is rejected with {@link ForbiddenException} (403) before any progress row is read.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherStudentProgressServiceImpl implements TeacherStudentProgressService {

    /** Same access-granting states the Admin and Student portals already treat as "has access". */
    private static final Set<EnrollmentStatus> ACCESS_STATES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final TeacherAuthorizationService authorization;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;

    @Override
    public List<TeacherStudentProgressResponse> courseStudents(Long teacherId, Long courseId) {
        // Backend-enforced (PART 10A / 11B-3): the course id from the URL is untrusted until this
        // call confirms it is actually assigned to the requesting teacher.
        Course course = authorization.requireAssignedCourse(teacherId, courseId);
        List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(course.getId());

        List<TeacherStudentProgressResponse> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByCourseId(course.getId())) {
            if (!ACCESS_STATES.contains(e.getStatus()) || e.getUser() == null) {
                continue;
            }
            User student = e.getUser();
            out.add(summarize(student, e, lessons));
        }
        return out;
    }

    @Override
    public List<TeacherStudentRosterResponse> myStudents(Long teacherId) {
        // The course list itself is already scoped to this teacher (Course.teacherId = teacherId) -
        // no course belonging to another teacher can ever appear here.
        List<TeacherStudentRosterResponse> out = new ArrayList<>();
        for (Course course : authorization.assignedCourses(teacherId)) {
            List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(course.getId());
            for (Enrollment e : enrollmentRepository.findByCourseId(course.getId())) {
                if (!ACCESS_STATES.contains(e.getStatus()) || e.getUser() == null) {
                    continue;
                }
                out.add(roster(e.getUser(), course, e, lessons));
            }
        }
        return out;
    }

    @Override
    public List<TeacherLessonProgressResponse> studentProgress(Long teacherId, Long courseId, Long studentId) {
        // Step 1: the course must belong to the requesting teacher.
        Course course = authorization.requireAssignedCourse(teacherId, courseId);
        // Step 2: the student id from the URL is untrusted - it must be genuinely enrolled in this
        // exact course, never merely "some student that exists somewhere in the system".
        enrollmentRepository.findFirstByUserIdAndCourseId(studentId, course.getId())
                .filter(e -> ACCESS_STATES.contains(e.getStatus()))
                .orElseThrow(() -> new ForbiddenException("This student is not enrolled in this course"));

        List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(course.getId());
        Map<Long, StudentLessonProgress> progressByLesson = new HashMap<>();
        for (StudentLessonProgress p : progressRepository.findByStudentAndCourse(studentId, course.getId())) {
            progressByLesson.put(p.getLesson().getId(), p);
        }

        List<TeacherLessonProgressResponse> out = new ArrayList<>();
        for (CourseLesson lesson : lessons) {
            StudentLessonProgress p = progressByLesson.get(lesson.getId());
            out.add(new TeacherLessonProgressResponse(
                    lesson.getId(),
                    lesson.getTitle(),
                    lesson.getModule() != null ? lesson.getModule().getId() : null,
                    lesson.getModule() != null ? lesson.getModule().getTitle() : null,
                    p != null && Boolean.TRUE.equals(p.getCompleted()),
                    p != null ? p.getLastWatchedAt() : null));
        }
        return out;
    }

    private TeacherStudentProgressResponse summarize(User student, Enrollment enrollment, List<CourseLesson> lessons) {
        ProgressTally tally = tally(student.getId(), enrollment.getCourse().getId(), lessons);
        return new TeacherStudentProgressResponse(
                student.getId(),
                student.getStudentLoginId(),
                student.getFullName(),
                student.getEmail(),
                enrollment.getStatus(),
                lessons.size(),
                tally.completed(),
                percentage(tally.completed(), lessons.size()),
                tally.lastWatched(),
                enrollment.getCreatedAt());
    }

    private TeacherStudentRosterResponse roster(User student, Course course, Enrollment enrollment, List<CourseLesson> lessons) {
        ProgressTally tally = tally(student.getId(), course.getId(), lessons);
        return new TeacherStudentRosterResponse(
                student.getId(),
                student.getStudentLoginId(),
                student.getFullName(),
                student.getEmail(),
                course.getId(),
                course.getTitle(),
                enrollment.getStatus(),
                lessons.size(),
                tally.completed(),
                percentage(tally.completed(), lessons.size()),
                tally.lastWatched(),
                enrollment.getCreatedAt());
    }

    private ProgressTally tally(Long studentId, Long courseId, List<CourseLesson> lessons) {
        Map<Long, StudentLessonProgress> progress = new HashMap<>();
        for (StudentLessonProgress p : progressRepository.findByStudentAndCourse(studentId, courseId)) {
            progress.put(p.getLesson().getId(), p);
        }
        int completed = 0;
        for (CourseLesson l : lessons) {
            StudentLessonProgress p = progress.get(l.getId());
            if (p != null && Boolean.TRUE.equals(p.getCompleted())) {
                completed++;
            }
        }
        java.time.LocalDateTime lastWatched = progress.values().stream()
                .map(StudentLessonProgress::getLastWatchedAt)
                .filter(Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null);
        return new ProgressTally(completed, lastWatched);
    }

    private static int percentage(int completed, int total) {
        return total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
    }

    private record ProgressTally(int completed, java.time.LocalDateTime lastWatched) {
    }
}

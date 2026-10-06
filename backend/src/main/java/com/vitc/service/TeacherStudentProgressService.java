package com.vitc.service;

import com.vitc.dto.response.TeacherLessonProgressResponse;
import com.vitc.dto.response.TeacherStudentProgressResponse;
import com.vitc.dto.response.TeacherStudentRosterResponse;
import java.util.List;

/**
 * PART 11B-3 - lets a Teacher see student progress, strictly scoped to courses assigned to that
 * Teacher. Every method re-verifies course ownership (and, for a single student's detail, that
 * the student is actually enrolled in that specific course) before returning anything - a course
 * id or student id supplied by the browser is never trusted on its own.
 */
public interface TeacherStudentProgressService {

    /** Every enrolled (active/completed) student's progress summary for one of the teacher's own courses. */
    List<TeacherStudentProgressResponse> courseStudents(Long teacherId, Long courseId);

    /** Per-lesson progress detail for one student, within one of the teacher's own courses. */
    List<TeacherLessonProgressResponse> studentProgress(Long teacherId, Long courseId, Long studentId);

    /**
     * PART 4/8 - the dedicated "Students" page: every enrolled (active/completed) student across
     * every course assigned to this teacher, in one call. Never includes another teacher's course
     * enrollments - the course list itself comes from {@link TeacherAuthorizationService#assignedCourses}.
     */
    List<TeacherStudentRosterResponse> myStudents(Long teacherId);
}

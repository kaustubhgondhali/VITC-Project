package com.vitc.service.impl;

import com.vitc.dto.request.ExamScheduleRequest;
import com.vitc.dto.response.ExamResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.Enrollment;
import com.vitc.entity.Exam;
import com.vitc.entity.StudentLessonProgress;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.ExamStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ForbiddenException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.ExamRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.service.ExamService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExamServiceImpl implements ExamService {
    private final ExamRepository examRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;

    @Override
    public List<ExamResponse> studentExams(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByUserIdOrderByIdDesc(studentId);
        List<ExamResponse> out = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Course c = e.getCourse();
            if (c == null || !Boolean.TRUE.equals(c.getActive())) {
                continue;
            }
            List<CourseLesson> activeLessons = lessonRepository.findActiveByCourseId(c.getId());
            if (activeLessons.isEmpty()) {
                continue;
            }
            Map<Long, StudentLessonProgress> progress = progressMap(studentId, c.getId());
            boolean allCompleted = activeLessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
            if (allCompleted) {
                if (e.getStatus() == EnrollmentStatus.ACTIVE) {
                    e.setStatus(EnrollmentStatus.COMPLETED);
                    enrollmentRepository.save(e);
                }
                Exam exam = examRepository.findByEnrollmentId(e.getId()).orElseGet(() -> examRepository.save(
                        Exam.builder()
                                .enrollment(e)
                                .completionDate(LocalDate.now())
                                .status(ExamStatus.ELIGIBLE)
                                .build()));
                out.add(ExamResponse.of(exam));
            } else {
                examRepository.findByEnrollmentId(e.getId()).ifPresent(exam -> {
                    if (exam.getStatus() == ExamStatus.SCHEDULED || exam.getStatus() == ExamStatus.APPLIED || exam.getStatus() == ExamStatus.COMPLETED) {
                        out.add(ExamResponse.of(exam));
                    }
                });
            }
        }
        return out;
    }

    @Override
    public ExamResponse apply(Long studentId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
        if (enrollment.getUser() == null || !studentId.equals(enrollment.getUser().getId())) {
            throw new ForbiddenException("You can only apply for exams in your own enrolled courses");
        }
        Course course = enrollment.getCourse();
        if (course == null || !Boolean.TRUE.equals(course.getActive())) {
            throw new ForbiddenException("This course is not active");
        }
        List<CourseLesson> activeLessons = lessonRepository.findActiveByCourseId(course.getId());
        if (activeLessons.isEmpty()) {
            throw new ForbiddenException("This course has no required lessons");
        }
        Map<Long, StudentLessonProgress> progress = progressMap(studentId, course.getId());
        boolean allCompleted = activeLessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
        if (!allCompleted) {
            throw new ForbiddenException("You must complete all required course lessons before applying for the exam");
        }
        Exam exam = examRepository.findByEnrollmentId(enrollmentId).orElseGet(() -> Exam.builder()
                .enrollment(enrollment)
                .completionDate(LocalDate.now())
                .build());
        if (exam.getStatus() == ExamStatus.APPLIED) {
            throw new BadRequestException("Exam application has already been submitted for this course");
        }
        if (exam.getStatus() == ExamStatus.SCHEDULED) {
            throw new BadRequestException("Exam is already scheduled for this course");
        }
        if (exam.getStatus() == ExamStatus.COMPLETED) {
            throw new BadRequestException("Exam has already been completed for this course");
        }
        if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollmentRepository.save(enrollment);
        }
        exam.setStatus(ExamStatus.APPLIED);
        exam.setAppliedDate(LocalDate.now());
        if (exam.getCompletionDate() == null) {
            exam.setCompletionDate(LocalDate.now());
        }
        return ExamResponse.of(examRepository.save(exam));
    }

    @Override
    public List<ExamResponse> teacherReady(Long teacherId) {
        List<ExamResponse> out = new ArrayList<>();
        List<Enrollment> enrollments = enrollmentRepository.findAll();
        for (Enrollment e : enrollments) {
            if (e.getCourse() == null || !teacherId.equals(e.getCourse().getTeacherId()) || e.getUser() == null) {
                continue;
            }
            List<CourseLesson> activeLessons = lessonRepository.findActiveByCourseId(e.getCourse().getId());
            if (activeLessons.isEmpty()) {
                continue;
            }
            Map<Long, StudentLessonProgress> progress = progressMap(e.getUser().getId(), e.getCourse().getId());
            boolean allCompleted = activeLessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
            if (allCompleted) {
                if (e.getStatus() == EnrollmentStatus.ACTIVE) {
                    e.setStatus(EnrollmentStatus.COMPLETED);
                    enrollmentRepository.save(e);
                }
                Exam exam = examRepository.findByEnrollmentId(e.getId()).orElseGet(() -> examRepository.save(
                        Exam.builder()
                                .enrollment(e)
                                .completionDate(e.getUpdatedAt() == null ? LocalDate.now() : e.getUpdatedAt().toLocalDate())
                                .status(ExamStatus.ELIGIBLE)
                                .build()));
                out.add(ExamResponse.of(exam));
            } else {
                examRepository.findByEnrollmentId(e.getId()).ifPresent(exam -> {
                    if (exam.getStatus() == ExamStatus.APPLIED || exam.getStatus() == ExamStatus.SCHEDULED || exam.getStatus() == ExamStatus.COMPLETED) {
                        out.add(ExamResponse.of(exam));
                    }
                });
            }
        }
        return out;
    }

    @Override
    public ExamResponse schedule(Long teacherId, Long enrollmentId, ExamScheduleRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));
        if (enrollment.getCourse() == null || !teacherId.equals(enrollment.getCourse().getTeacherId()) || enrollment.getUser() == null) {
            throw new ForbiddenException("You can only schedule exams for completed students in your assigned courses");
        }
        List<CourseLesson> activeLessons = lessonRepository.findActiveByCourseId(enrollment.getCourse().getId());
        Map<Long, StudentLessonProgress> progress = progressMap(enrollment.getUser().getId(), enrollment.getCourse().getId());
        boolean allCompleted = !activeLessons.isEmpty() && activeLessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
        if (!allCompleted) {
            throw new ForbiddenException("Student has not completed all required course lessons");
        }
        Exam exam = examRepository.findByEnrollmentId(enrollmentId).orElseGet(() -> Exam.builder()
                .enrollment(enrollment).completionDate(LocalDate.now()).build());
        exam.setStatus(ExamStatus.SCHEDULED);
        exam.setExamDate(request.examDate());
        exam.setExamTime(request.examTime());
        exam.setMode(request.mode());
        exam.setLocationOrLink(request.locationOrLink());
        exam.setNotes(request.notes());
        return ExamResponse.of(examRepository.save(exam));
    }

    private Map<Long, StudentLessonProgress> progressMap(Long studentId, Long courseId) {
        Map<Long, StudentLessonProgress> map = new HashMap<>();
        for (StudentLessonProgress p : progressRepository.findByStudentAndCourse(studentId, courseId)) {
            map.put(p.getLesson().getId(), p);
        }
        return map;
    }

    private static boolean isCompleted(StudentLessonProgress p) {
        return p != null && Boolean.TRUE.equals(p.getCompleted());
    }
}

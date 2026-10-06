package com.vitc.service.impl;

import com.vitc.dto.request.AdminEnrollmentRequest;
import com.vitc.dto.response.AdminStudentCourseResponse;
import com.vitc.dto.response.AdminStudentDetailResponse;
import com.vitc.dto.response.AdminStudentResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.Enrollment;
import com.vitc.entity.StudentLessonProgress;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.AdminStudentService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Admin -> Students. Reads the SAME entities the student portal uses; no
 * duplicate student/status model is introduced. Password hashes, temporary
 * passwords and session tokens are never copied into a response DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStudentServiceImpl implements AdminStudentService {

    /** Same access-granting states the student learning service enforces. */
    private static final Set<EnrollmentStatus> ACCESS_STATES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    /** Marker kept in enrollment.notes so a granted course never looks paid. */
    public static final String MANUAL_MARKER = "MANUALLY GRANTED";

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseLessonRepository lessonRepository;
    private final StudentLessonProgressRepository progressRepository;

    @Override
    public List<AdminStudentResponse> list() {
        List<User> students = new ArrayList<>(userRepository.findByRole(UserRole.STUDENT));
        students.sort(Comparator.comparing(User::getId).reversed());
        List<AdminStudentResponse> out = new ArrayList<>();
        for (User s : students) {
            List<String> titles = new ArrayList<>();
            for (Enrollment e : enrollmentRepository.findByUserIdOrderByIdDesc(s.getId())) {
                if (e.getCourse() != null) {
                    titles.add(e.getCourse().getTitle());
                }
            }
            out.add(new AdminStudentResponse(
                    s.getId(),
                    s.getStudentLoginId(),
                    s.getFullName(),
                    s.getEmail(),
                    s.getPhone(),
                    s.getStatus(),
                    titles,
                    titles.size(),
                    s.getLastLoginAt(),
                    s.getCreatedAt()));
        }
        return out;
    }

    @Override
    public AdminStudentDetailResponse detail(Long studentId) {
        return toDetail(student(studentId));
    }

    @Override
    public List<AdminStudentCourseResponse> courses(Long studentId) {
        return enrollmentSummaries(student(studentId));
    }

    @Override
    @Transactional
    public AdminStudentDetailResponse grantEnrollment(Long studentId,
                                                      AdminEnrollmentRequest request,
                                                      String adminUsername) {
        User student = student(studentId);
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        Enrollment existing = enrollmentRepository
                .findFirstByUserIdAndCourseId(student.getId(), course.getId())
                .orElse(null);
        if (existing != null) {
            if (ACCESS_STATES.contains(existing.getStatus())) {
                throw new BadRequestException("This student already has access to " + course.getTitle());
            }
            // Activate the existing (e.g. PENDING/CANCELLED) enrolment instead of
            // creating a duplicate. Payment records are left untouched.
            existing.setStatus(EnrollmentStatus.ACTIVE);
            existing.setNotes(appendNote(existing.getNotes(), request.reason(), adminUsername));
            enrollmentRepository.save(existing);
            return toDetail(student);
        }

        Enrollment granted = Enrollment.builder()
                .studentName(student.getFullName())
                .email(student.getEmail())
                .phone(student.getPhone() == null || student.getPhone().isBlank() ? "-" : student.getPhone())
                .course(course)
                .user(student)
                .amount(null) // no money changed hands: never fake a paid order
                .status(EnrollmentStatus.ACTIVE)
                .notes(appendNote(null, request.reason(), adminUsername))
                .build();
        enrollmentRepository.save(granted);
        return toDetail(student);
    }

    /* ========================= helpers ========================= */

    private static String appendNote(String current, String reason, String adminUsername) {
        String note = MANUAL_MARKER + " by " + (adminUsername == null ? "admin" : adminUsername)
                + " on " + LocalDateTime.now()
                + (reason == null || reason.isBlank() ? "" : " — " + reason.trim());
        return current == null || current.isBlank() ? note : current + "\n" + note;
    }

    private User student(Long studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (user.getRole() != UserRole.STUDENT) {
            throw new ResourceNotFoundException("Student not found");
        }
        return user;
    }

    private AdminStudentDetailResponse toDetail(User s) {
        return new AdminStudentDetailResponse(
                s.getId(),
                s.getStudentLoginId(),
                s.getFullName(),
                s.getEmail(),
                s.getPhone(),
                s.getCity(),
                s.getStatus(),
                Boolean.TRUE.equals(s.getMustChangePassword()),
                s.getLastLoginAt(),
                s.getCreatedAt(),
                enrollmentSummaries(s));
    }

    private List<AdminStudentCourseResponse> enrollmentSummaries(User student) {
        List<AdminStudentCourseResponse> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByUserIdOrderByIdDesc(student.getId())) {
            Course c = e.getCourse();
            if (c == null) {
                continue;
            }
            List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(c.getId());
            Map<Long, StudentLessonProgress> progress = new HashMap<>();
            for (StudentLessonProgress p : progressRepository.findByStudentAndCourse(student.getId(), c.getId())) {
                progress.put(p.getLesson().getId(), p);
            }
            int completed = 0;
            for (CourseLesson l : lessons) {
                StudentLessonProgress p = progress.get(l.getId());
                if (p != null && Boolean.TRUE.equals(p.getCompleted())) {
                    completed++;
                }
            }
            LocalDateTime lastWatched = progress.values().stream()
                    .map(StudentLessonProgress::getLastWatchedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            out.add(new AdminStudentCourseResponse(
                    e.getId(),
                    c.getId(),
                    c.getCode(),
                    c.getTitle(),
                    e.getStatus(),
                    ACCESS_STATES.contains(e.getStatus()),
                    e.getNotes() != null && e.getNotes().contains(MANUAL_MARKER),
                    lessons.size(),
                    completed,
                    lessons.isEmpty() ? 0 : (int) Math.round((completed * 100.0) / lessons.size()),
                    lastWatched,
                    e.getCreatedAt()));
        }
        return out;
    }
}

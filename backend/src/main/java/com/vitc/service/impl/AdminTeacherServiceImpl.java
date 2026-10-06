package com.vitc.service.impl;

import com.vitc.dto.request.AdminTeacherRequest;
import com.vitc.dto.request.AdminTeacherUpdateRequest;
import com.vitc.dto.request.TeacherCourseAssignmentRequest;
import com.vitc.dto.request.TeacherPasswordResetRequest;
import com.vitc.dto.response.AdminTeacherCourseResponse;
import com.vitc.dto.response.AdminTeacherResponse;
import com.vitc.dto.response.TeacherCredentialsResponse;
import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.UserRepository;
import com.vitc.service.AdminTeacherService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 10C implementation. Reuses the existing {@code users} table (role
 * TEACHER) and the existing {@code courses.teacher_id} assignment column, so
 * nothing from PARTS 1-9 changes shape.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTeacherServiceImpl implements AdminTeacherService {

    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;

    /* ------------------------------------------------------------------ read */

    @Override
    public List<AdminTeacherResponse> list() {
        List<User> teachers = new ArrayList<>(userRepository.findByRole(UserRole.TEACHER));
        teachers.sort(Comparator.comparing(User::getId));
        Map<Long, List<Course>> byTeacher = new HashMap<>();
        for (Course course : courseRepository.findAll()) {
            if (course.getTeacherId() != null) {
                byTeacher.computeIfAbsent(course.getTeacherId(), k -> new ArrayList<>()).add(course);
            }
        }
        Map<Long, String> names = teacherNames(teachers);
        return teachers.stream()
                .map(t -> toResponse(t, byTeacher.getOrDefault(t.getId(), List.of()), names))
                .toList();
    }

    @Override
    public AdminTeacherResponse get(Long teacherId) {
        User teacher = requireTeacherRow(teacherId);
        return toResponse(teacher, courseRepository.findByTeacherId(teacherId),
                teacherNames(userRepository.findByRole(UserRole.TEACHER)));
    }

    @Override
    public List<AdminTeacherCourseResponse> assignedCourses(Long teacherId) {
        requireTeacherRow(teacherId);
        Map<Long, String> names = teacherNames(userRepository.findByRole(UserRole.TEACHER));
        return courseRepository.findByTeacherId(teacherId).stream()
                .map(c -> toCourse(c, names))
                .toList();
    }

    @Override
    public List<AdminTeacherCourseResponse> allCourses() {
        Map<Long, String> names = teacherNames(userRepository.findByRole(UserRole.TEACHER));
        return courseRepository.findAll().stream()
                .sorted(Comparator.comparing(Course::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> toCourse(c, names))
                .toList();
    }

    /* ----------------------------------------------------------------- write */

    @Override
    @Transactional
    public TeacherCredentialsResponse create(AdminTeacherRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String email = request.email() == null ? "" : request.email().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("A user with username '" + username + "' already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A user with email '" + email + "' already exists");
        }

        boolean generated = request.password() == null || request.password().isBlank();
        String password = generated ? generatePassword() : request.password();

        User teacher = new User();
        teacher.setFullName(request.fullName().trim());
        teacher.setUsername(username);
        teacher.setEmail(email);
        teacher.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        // Only the BCrypt hash is ever persisted - never the plain text.
        teacher.setPasswordHash(passwordEncoder.encode(password));
        teacher.setRole(UserRole.TEACHER);
        teacher.setStatus(UserStatus.ACTIVE);
        teacher.setMustChangePassword(true);
        teacher = userRepository.save(teacher);

        if (request.courseIds() != null && !request.courseIds().isEmpty()) {
            applyAssignments(teacher.getId(), request.courseIds());
        }
        log.info("Teacher account created by Main Admin -> username: {}", username);
        return new TeacherCredentialsResponse(teacher.getId(), username, password, true);
    }

    @Override
    @Transactional
    public AdminTeacherResponse update(Long teacherId, AdminTeacherUpdateRequest request) {
        User teacher = requireTeacherRow(teacherId);
        String email = request.email().trim();
        userRepository.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (!other.getId().equals(teacherId)) {
                throw new DuplicateResourceException("A user with email '" + email + "' already exists");
            }
        });
        teacher.setFullName(request.fullName().trim());
        teacher.setEmail(email);
        teacher.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        userRepository.save(teacher);
        return get(teacherId);
    }

    @Override
    @Transactional
    public AdminTeacherResponse updateStatus(Long teacherId, UserStatus status) {
        if (status == null) {
            throw new BadRequestException("Status is required");
        }
        if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) {
            throw new BadRequestException("A teacher status must be ACTIVE or INACTIVE");
        }
        User teacher = requireTeacherRow(teacherId);
        teacher.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            // Kill the live session immediately: an already signed-in teacher loses
            // access on the very next request (TeacherAuthInterceptor -> 401/403).
            teacher.setSessionToken(null);
            teacher.setSessionExpiresAt(null);
        }
        userRepository.save(teacher);
        // NOTE: no content is deleted here. Modules, lessons, videos, progress and
        // every historical record created by this teacher are preserved as-is.
        return get(teacherId);
    }

    @Override
    @Transactional
    public AdminTeacherResponse assignCourses(Long teacherId, TeacherCourseAssignmentRequest request) {
        requireTeacherRow(teacherId);
        applyAssignments(teacherId, request == null || request.courseIds() == null ? List.of() : request.courseIds());
        return get(teacherId);
    }

    @Override
    @Transactional
    public AdminTeacherResponse removeCourse(Long teacherId, Long courseId) {
        requireTeacherRow(teacherId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
        if (!teacherId.equals(course.getTeacherId())) {
            throw new BadRequestException("That course is not assigned to this teacher");
        }
        course.setTeacherId(null);
        courseRepository.save(course);
        // Authorisation only: the course, its modules and lessons stay untouched.
        return get(teacherId);
    }

    @Override
    @Transactional
    public TeacherCredentialsResponse resetPassword(Long teacherId, TeacherPasswordResetRequest request) {
        User teacher = requireTeacherRow(teacherId);
        boolean generated = request == null || request.newPassword() == null || request.newPassword().isBlank();
        String password = generated ? generatePassword() : request.newPassword();
        if (password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long");
        }
        teacher.setPasswordHash(passwordEncoder.encode(password));
        teacher.setMustChangePassword(true);
        teacher.setSessionToken(null);
        teacher.setSessionExpiresAt(null);
        userRepository.save(teacher);
        log.info("Teacher password reset by Main Admin -> username: {}", teacher.getUsername());
        return new TeacherCredentialsResponse(teacher.getId(), teacher.getUsername(), password, true);
    }

    /* --------------------------------------------------------------- helpers */

    private void applyAssignments(Long teacherId, List<Long> courseIds) {
        Set<Long> wanted = new LinkedHashSet<>();
        for (Long id : courseIds) {
            if (id != null) {
                wanted.add(id);
            }
        }
        for (Long courseId : wanted) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
            if (course.getTeacherId() != null && !course.getTeacherId().equals(teacherId)) {
                throw new BadRequestException("Course '" + course.getTitle()
                        + "' is already assigned to another teacher. Remove that assignment first.");
            }
            course.setTeacherId(teacherId);
            courseRepository.save(course);
        }
        // Withdraw authorisation for courses no longer in the list (content preserved).
        for (Course current : courseRepository.findByTeacherId(teacherId)) {
            if (!wanted.contains(current.getId())) {
                current.setTeacherId(null);
                courseRepository.save(current);
            }
        }
    }

    private User requireTeacherRow(Long teacherId) {
        User user = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        if (user.getRole() != UserRole.TEACHER) {
            throw new ResourceNotFoundException("Teacher not found: " + teacherId);
        }
        return user;
    }

    private Map<Long, String> teacherNames(List<User> teachers) {
        Map<Long, String> names = new HashMap<>();
        teachers.forEach(t -> names.put(t.getId(), t.getFullName()));
        return names;
    }

    private AdminTeacherResponse toResponse(User teacher, List<Course> courses, Map<Long, String> names) {
        List<AdminTeacherCourseResponse> assigned = courses.stream()
                .sorted(Comparator.comparing(Course::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> toCourse(c, names))
                .toList();
        return new AdminTeacherResponse(
                teacher.getId(),
                teacher.getFullName(),
                teacher.getUsername(),
                teacher.getEmail(),
                teacher.getPhone(),
                teacher.getStatus(),
                Boolean.TRUE.equals(teacher.getMustChangePassword()),
                assigned,
                assigned.size(),
                teacher.getLastLoginAt(),
                teacher.getCreatedAt());
    }

    private AdminTeacherCourseResponse toCourse(Course course, Map<Long, String> names) {
        return new AdminTeacherCourseResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getCategory(),
                course.getActive(),
                course.getTeacherId(),
                course.getTeacherId() == null ? null : names.get(course.getTeacherId()));
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}

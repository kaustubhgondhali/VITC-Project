package com.vitc.service.impl;

import com.vitc.dto.request.LessonProgressRequest;
import com.vitc.dto.response.StudentCourseDetailResponse;
import com.vitc.dto.response.StudentCourseSummaryResponse;
import com.vitc.dto.response.StudentLessonDetailResponse;
import com.vitc.dto.response.StudentLessonResponse;
import com.vitc.dto.response.StudentModuleResponse;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.Enrollment;
import com.vitc.entity.Exam;
import com.vitc.entity.StudentLessonProgress;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.exception.ForbiddenException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.ExamRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.repository.UserRepository;
import com.vitc.security.VideoAccessTokenService;
import com.vitc.service.StudentLearningService;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentLearningServiceImpl implements StudentLearningService {

    /** Enrolment states that grant course access. */
    private static final Set<EnrollmentStatus> ACCESS_STATES =
            EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    /**
     * Lessons unlock in study order: everything up to the first unfinished
     * lesson, plus a short look-ahead so students are never hard-blocked by a
     * single lesson they skipped.
     */
    private static final int LOOK_AHEAD = 2;

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamRepository examRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;
    private final VideoAccessTokenService videoAccessTokenService;

    @org.springframework.beans.factory.annotation.Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    /** Folder lesson videos are stored under - must match FileStorageServiceImpl. */
    private static final String VIDEO_FOLDER = "videos";
    private final StudentLessonProgressRepository progressRepository;

    /* ========================= My Courses ========================= */

    @Override
    @Transactional
    public List<StudentCourseSummaryResponse> myCourses(Long studentId) {
        User student = student(studentId);
        reconcileStudentEnrollments(student);
        List<StudentCourseSummaryResponse> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepository.findByUserIdOrderByIdDesc(student.getId())) {
            Course c = e.getCourse();
            if (c == null || !ACCESS_STATES.contains(e.getStatus()) || !Boolean.TRUE.equals(c.getActive())) {
                continue;
            }
            List<CourseLesson> lessons = publishedLessons(c.getId());
            Map<Long, StudentLessonProgress> progress = progressMap(student.getId(), c.getId());
            int completed = (int) lessons.stream().filter(l -> isCompleted(progress.get(l.getId()))).count();
            CourseLesson resume = resumeLesson(lessons, progress);
            StudentLessonProgress lastWatchedRow = progress.values().stream()
                    .filter(p -> p.getLastWatchedAt() != null)
                    .max(java.util.Comparator.comparing(StudentLessonProgress::getLastWatchedAt))
                    .orElse(null);
            CourseLesson lastAccessedLesson = lastWatchedRow == null ? null : lastWatchedRow.getLesson();
            out.add(new StudentCourseSummaryResponse(
                    e.getId(),
                    c.getId(),
                    c.getCode(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getIcon(),
                    instructorName(c.getTeacherId()),
                    c.getLevel(),
                    c.getCategory(),
                    c.getDurationMonths(),
                    e.getStatus(),
                    ACCESS_STATES.contains(e.getStatus()),
                    lessons.size(),
                    completed,
                    percentage(completed, lessons.size()),
                    resume == null ? null : resume.getId(),
                    resume == null ? null : resume.getTitle(),
                    lastAccessedLesson == null ? null : lastAccessedLesson.getId(),
                    lastAccessedLesson == null ? null : lastAccessedLesson.getTitle(),
                    lastWatchedRow == null ? null : lastWatchedRow.getLastWatchedAt(),
                    e.getCreatedAt()));
        }
        return out;
    }

    /**
     * Real instructor name for the course's assigned Teacher Admin, when one
     * is assigned ({@code Course.teacherId}). No fabricated names: courses
     * without an assigned teacher simply return {@code null} and the
     * frontend falls back to a neutral, non-specific label.
     */
    private String instructorName(Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        return userRepository.findById(teacherId)
                .filter(u -> u.getRole() == UserRole.TEACHER)
                .map(User::getFullName)
                .orElse(null);
    }

    /* ========================= Course + modules ========================= */

    @Override
    @Transactional
    public StudentCourseDetailResponse course(Long studentId, Long courseId) {
        User student = student(studentId);
        Enrollment enrollment = requireEnrollment(student, courseId);
        Course course = enrollment.getCourse();

        List<CourseLesson> lessons = publishedLessons(courseId);
        Map<Long, StudentLessonProgress> progress = progressMap(student.getId(), courseId);
        List<StudentModuleResponse> modules = buildModules(courseId, lessons, progress);

        int completed = (int) lessons.stream().filter(l -> isCompleted(progress.get(l.getId()))).count();
        CourseLesson resume = resumeLesson(lessons, progress);

        return new StudentCourseDetailResponse(
                course.getId(),
                course.getCode(),
                course.getTitle(),
                course.getDescription(),
                course.getLevel(),
                course.getCategory(),
                course.getDurationMonths(),
                enrollment.getStatus(),
                lessons.size(),
                completed,
                percentage(completed, lessons.size()),
                resume == null ? null : resume.getId(),
                modules);
    }

    @Override
    @Transactional
    public List<StudentModuleResponse> modules(Long studentId, Long courseId) {
        User student = student(studentId);
        requireEnrollment(student, courseId);
        List<CourseLesson> lessons = publishedLessons(courseId);
        return buildModules(courseId, lessons, progressMap(student.getId(), courseId));
    }

    private List<StudentModuleResponse> buildModules(Long courseId,
                                                     List<CourseLesson> orderedLessons,
                                                     Map<Long, StudentLessonProgress> progress) {
        int unlockLimit = unlockLimit(orderedLessons, progress);
        Map<Long, Integer> lessonIndex = new HashMap<>();
        for (int i = 0; i < orderedLessons.size(); i++) {
            lessonIndex.put(orderedLessons.get(i).getId(), i);
        }

        List<StudentModuleResponse> out = new ArrayList<>();
        for (CourseModule m : moduleRepository.findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc(courseId)) {
            List<StudentLessonResponse> lessons = new ArrayList<>();
            int completed = 0;
            for (CourseLesson l : lessonRepository
                    .findByModuleIdAndActiveTrueOrderByDisplayOrderAscIdAsc(m.getId())) {
                if (!hasMedia(l)) {
                    continue;
                }
                StudentLessonProgress p = progress.get(l.getId());
                boolean done = isCompleted(p);
                if (done) {
                    completed++;
                }
                int index = lessonIndex.getOrDefault(l.getId(), 0);
                lessons.add(new StudentLessonResponse(
                        l.getId(),
                        m.getId(),
                        l.getTitle(),
                        l.getDescription(),
                        l.getDuration(),
                        l.getDisplayOrder(),
                        done,
                        p == null || p.getProgressPercentage() == null ? 0 : p.getProgressPercentage(),
                        !done && index > unlockLimit,
                        p == null ? null : p.getLastWatchedAt()));
            }
            out.add(new StudentModuleResponse(
                    m.getId(),
                    courseId,
                    m.getTitle(),
                    m.getDescription(),
                    m.getDisplayOrder(),
                    lessons.size(),
                    completed,
                    percentage(completed, lessons.size()),
                    lessons));
        }
        return out;
    }

    /* ========================= Lesson + video ========================= */

    @Override
    @Transactional
    public StudentLessonDetailResponse lesson(Long studentId, Long lessonId) {
        User student = student(studentId);
        CourseLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));
        if (!Boolean.TRUE.equals(lesson.getActive())) {
            throw new ForbiddenException("This lesson is not available");
        }
        CourseModule module = lesson.getModule();
        if (module == null || !Boolean.TRUE.equals(module.getActive())) {
            throw new ForbiddenException("This lesson is not available");
        }
        Course course = module.getCourse();
        requireEnrollment(student, course.getId());
        if (!hasMedia(lesson)) {
            throw new ForbiddenException("This lesson has no playable media");
        }

        List<CourseLesson> ordered = publishedLessons(course.getId());
        Map<Long, StudentLessonProgress> progress = progressMap(student.getId(), course.getId());
        int index = indexOf(ordered, lessonId);
        if (index < 0) {
            throw new ForbiddenException("This lesson is not part of your course");
        }
        StudentLessonProgress own = progress.get(lessonId);
        if (!isCompleted(own) && index > unlockLimit(ordered, progress)) {
            throw new ForbiddenException("Finish the earlier lessons to unlock this one");
        }

        int completed = (int) ordered.stream().filter(l -> isCompleted(progress.get(l.getId()))).count();
        return new StudentLessonDetailResponse(
                lesson.getId(),
                module.getId(),
                module.getTitle(),
                course.getId(),
                course.getTitle(),
                lesson.getTitle(),
                lesson.getDescription(),
                lesson.getDuration(),
                playableUrl(student.getId(), lesson),
                videoType(lesson.getVideoUrl()),
                playableAudioUrl(student.getId(), lesson),
                isCompleted(own),
                own == null || own.getProgressPercentage() == null ? 0 : own.getProgressPercentage(),
                index > 0 ? ordered.get(index - 1).getId() : null,
                index < ordered.size() - 1 ? ordered.get(index + 1).getId() : null,
                index + 1,
                ordered.size(),
                percentage(completed, ordered.size()));
    }

    @Override
    @Transactional
    public StudentLessonDetailResponse saveProgress(Long studentId, Long lessonId, LessonProgressRequest request) {
        User student = student(studentId);
        CourseLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));
        CourseModule module = lesson.getModule();
        if (module == null || !Boolean.TRUE.equals(module.getActive())
            || !Boolean.TRUE.equals(lesson.getActive()) || !hasMedia(lesson)) {
            throw new ForbiddenException("This lesson is not available");
        }
        Long courseId = module.getCourse().getId();
        requireEnrollment(student, courseId);

        StudentLessonProgress row = progressRepository
                .findByStudentIdAndLessonId(student.getId(), lessonId)
                .orElseGet(() -> StudentLessonProgress.builder()
                        .student(student)
                        .lesson(lesson)
                        .completed(false)
                        .progressPercentage(0)
                        .build());

        boolean completed = request.completed() == null
            ? Boolean.TRUE.equals(row.getCompleted())
            : request.completed();
        int pct = request.progressPercentage() == null
                ? (row.getProgressPercentage() == null ? 0 : row.getProgressPercentage())
                : Math.max(0, Math.min(100, request.progressPercentage()));
        if (completed) {
            pct = 100;
            if (row.getCompletedAt() == null) {
                row.setCompletedAt(LocalDateTime.now());
            }
        } else if (Boolean.FALSE.equals(request.completed())) {
            pct = request.progressPercentage() == null ? 0 : pct;
            row.setCompletedAt(null);
        }
        row.setCompleted(completed);
        row.setProgressPercentage(pct);
        row.setLastWatchedAt(LocalDateTime.now());
        progressRepository.save(row);

        if (completed) {
            maybeCompleteCourse(student.getId(), courseId);
        }

        return lesson(student.getId(), lessonId);
    }

    /**
     * Course completion, PART 4: once every active lesson in the course has a
     * completed progress row for this student, flip the enrolment itself to
     * {@code COMPLETED} using the existing enrolment/progress architecture -
     * no new table, no certificate (that is a later phase). Only an
     * {@code ACTIVE} enrolment is promoted; anything already {@code COMPLETED}
     * or in another state (e.g. {@code CANCELLED}) is left untouched.
     */
    private void maybeCompleteCourse(Long studentId, Long courseId) {
        List<CourseLesson> lessons = lessonRepository.findActiveByCourseId(courseId);
        if (lessons.isEmpty()) {
            return;
        }
        Map<Long, StudentLessonProgress> progress = progressMap(studentId, courseId);
        boolean allCompleted = lessons.stream().allMatch(l -> isCompleted(progress.get(l.getId())));
        if (!allCompleted) {
            return;
        }
        enrollmentRepository.findFirstByUserIdAndCourseId(studentId, courseId).ifPresent(enrollment -> {
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                enrollment.setStatus(EnrollmentStatus.COMPLETED);
                enrollmentRepository.save(enrollment);
                examRepository.findByEnrollmentId(enrollment.getId()).orElseGet(() -> examRepository.save(
                    Exam.builder().enrollment(enrollment)
                        .completionDate(LocalDate.now())
                        .status(com.vitc.entity.enums.ExamStatus.ELIGIBLE).build()));
                log.info("Course {} marked COMPLETED for student {}", courseId, studentId);
            }
        });
    }

    /* ========================= Guards & helpers ========================= */

    private User student(Long studentId) {
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new ForbiddenException("Session expired, please sign in again"));
        if (user.getRole() != UserRole.STUDENT || user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("This student account cannot access the learning portal");
        }
        return user;
    }

    /**
     * PART 14 - repairs legacy/admin-created enrolments so a purchased course is never
     * invisible in My Courses just because the enrolment row predates the student's
     * account, or was created directly (e.g. by an admin) rather than through checkout.
     *
     * <p>Only ever touches an enrolment where {@code enrollment.getUser() == null} AND
     * the enrolment's email matches the AUTHENTICATED student's email (case-insensitive).
     * Identity is decided on email alone - never name, phone, or course title - and an
     * enrolment that is already linked to a (possibly different) student account is never
     * reassigned. This makes the repair safe to run on every request: an enrolment with no
     * matching, unlinked email rows is simply a no-op, and running it twice links nothing
     * a second time. Must only be called from within an existing write ({@code @Transactional},
     * non-read-only) method on this class - see {@code myCourses}/{@code requireEnrollment}'s
     * callers - since Spring's proxy-based AOP does not intercept this internal call.</p>
     */
    private void reconcileStudentEnrollments(User student) {
        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        List<Enrollment> unlinked = enrollmentRepository.findByEmailIgnoreCase(email).stream()
                .filter(e -> e.getUser() == null)
                .toList();
        if (unlinked.isEmpty()) {
            return;
        }
        unlinked.forEach(e -> e.setUser(student));
        enrollmentRepository.saveAll(unlinked);
        log.info("Reconciled {} legacy enrolment(s) to student {}", unlinked.size(), student.getStudentLoginId());
    }

    /**
     * The authorisation boundary: an enrolment must exist for THIS student and
     * THIS course and be in an access-granting state. Any other course id -
     * however it was guessed - ends up as 403 with no content returned.
     */
    private Enrollment requireEnrollment(User student, Long courseId) {
        if (courseId == null) {
            throw new ForbiddenException("You do not have access to this course");
        }
        reconcileStudentEnrollments(student);
        Enrollment enrollment = enrollmentRepository
                .findFirstByUserIdAndCourseId(student.getId(), courseId)
                .orElseThrow(() -> new ForbiddenException("You do not have access to this course"));
        if (!ACCESS_STATES.contains(enrollment.getStatus())) {
            throw new ForbiddenException("Your enrolment for this course is not active yet");
        }
        if (enrollment.getCourse() == null) {
            throw new ForbiddenException("You do not have access to this course");
        }
        if (!Boolean.TRUE.equals(enrollment.getCourse().getActive())) {
            throw new ForbiddenException("This course is not available");
        }
        return enrollment;
    }

    private List<CourseLesson> publishedLessons(Long courseId) {
        return lessonRepository.findActiveByCourseId(courseId).stream()
                .filter(StudentLearningServiceImpl::hasMedia)
                .toList();
    }

    private static boolean hasMedia(CourseLesson lesson) {
        return (lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank())
                || (lesson.getAudioUrl() != null && !lesson.getAudioUrl().isBlank());
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

    /** Highest lesson index the student may open. */
    private static int unlockLimit(List<CourseLesson> ordered, Map<Long, StudentLessonProgress> progress) {
        int firstIncomplete = ordered.size();
        for (int i = 0; i < ordered.size(); i++) {
            if (!isCompleted(progress.get(ordered.get(i).getId()))) {
                firstIncomplete = i;
                break;
            }
        }
        return firstIncomplete + LOOK_AHEAD;
    }

    /**
     * Continue Learning target, in priority order (PART 4):
     * 1. the incomplete lesson the student most recently watched anything of
     *    (progress row exists, not completed, has a lastWatchedAt) - resumes
     *    exactly where they left off even if they skipped ahead or back;
     * 2. otherwise the first incomplete lesson in course order;
     * 3. otherwise (every active lesson already completed, or the course has
     *    no progress rows at all) the first available lesson.
     */
    private static CourseLesson resumeLesson(List<CourseLesson> ordered, Map<Long, StudentLessonProgress> progress) {
        CourseLesson lastAccessedIncomplete = null;
        LocalDateTime latest = null;
        for (CourseLesson l : ordered) {
            StudentLessonProgress p = progress.get(l.getId());
            if (p != null && !isCompleted(p) && p.getLastWatchedAt() != null
                    && (latest == null || p.getLastWatchedAt().isAfter(latest))) {
                latest = p.getLastWatchedAt();
                lastAccessedIncomplete = l;
            }
        }
        if (lastAccessedIncomplete != null) {
            return lastAccessedIncomplete;
        }
        for (CourseLesson l : ordered) {
            if (!isCompleted(progress.get(l.getId()))) {
                return l;
            }
        }
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    private static int indexOf(List<CourseLesson> ordered, Long lessonId) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(lessonId)) {
                return i;
            }
        }
        return -1;
    }

    private static int percentage(int completed, int total) {
        return total <= 0 ? 0 : (int) Math.round((completed * 100.0) / total);
    }

    /**
     * PART 6C-2A/8 - the url actually handed to the browser for {@code StudentLessonDetailResponse.videoUrl}.
     *
     * <p>A locally-hosted lesson video (stored under {@code uploads/videos/} by
     * {@code FileStorageServiceImpl}) is never returned as its raw {@code /uploads/videos/...}
     * path any more - that path is now blocked from direct access (see
     * {@code VideoDirectAccessInterceptor}). Instead this mints a short-lived, signed,
     * lesson-and-student-scoped token and points the player at the protected streaming
     * endpoint. An external url (YouTube, Vimeo, or any other https link) is returned exactly
     * as stored - it is never treated as a local filesystem path and never migrated.</p>
     */
    private String playableUrl(Long studentId, CourseLesson lesson) {
        String url = lesson.getVideoUrl();
        String managedPrefix = publicPath + "/" + VIDEO_FOLDER + "/";
        if (url == null || !url.startsWith(managedPrefix)) {
            return url;
        }
        String token = videoAccessTokenService.issue(studentId, lesson.getId());
        return "/api/v1/student/lessons/" + lesson.getId() + "/video?token=" + token;
    }

    private String playableAudioUrl(Long studentId, CourseLesson lesson) {
        String url = lesson.getAudioUrl();
        String managedPrefix = publicPath + "/audio/";
        if (url == null || !url.startsWith(managedPrefix)) {
            return url;
        }
        String token = videoAccessTokenService.issue(studentId, lesson.getId());
        return "/api/v1/student/lessons/" + lesson.getId() + "/audio?token=" + token;
    }

    /** Lets the player choose an embed strategy without parsing urls in the browser. */
    private static String videoType(String url) {
        if (url == null || url.isBlank()) {
            return "NONE";
        }
        String u = url.toLowerCase(java.util.Locale.ROOT);
        if (u.contains("youtube.com") || u.contains("youtu.be")) {
            return "YOUTUBE";
        }
        if (u.contains("vimeo.com")) {
            return "VIMEO";
        }
        // PART 2/10 — VIDEO UPLOAD: every container the teacher can now upload is served by the
        // streaming endpoint and must render in the <video> player, not in an iframe.
        int dot = u.lastIndexOf('.');
        String ext = dot < 0 ? "" : u.substring(dot + 1).replaceAll("[^a-z0-9]", "");
        if (com.vitc.security.upload.VideoFormats.isVideoExtension(ext)
                || "ogg".equals(ext) || "m3u8".equals(ext)) {
            return "FILE";
        }
        return "EMBED";
    }
}

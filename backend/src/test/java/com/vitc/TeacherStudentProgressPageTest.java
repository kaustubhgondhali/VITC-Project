package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.Enrollment;
import com.vitc.entity.StudentLessonProgress;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.StudentLessonProgressRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 5/8 - Teacher "Student Progress" experience.
 *
 * <p>Covers the two endpoints reused (unchanged) from the existing backend:
 * {@code GET /api/v1/teacher/courses/{courseId}/students} (course summary) and
 * {@code GET /api/v1/teacher/courses/{courseId}/students/{studentId}/progress} (lesson detail).
 * Verifies the completion percentage, completed/total lesson counts, last-activity timestamp,
 * per-lesson module/lesson detail, the 0% and 100% edge cases, and that a course id or student id
 * belonging to another teacher/course is rejected with 403 before any progress row is read.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherStudentProgressPageTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;
    @Autowired StudentLessonProgressRepository progress;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";

    private Long courseId;
    private Long otherTeacherCourseId;
    private Long studentHalfId;   // 1 of 2 lessons completed -> 50%
    private Long studentZeroId;   // enrolled, nothing completed -> 0%
    private Long studentFullId;   // both lessons completed -> 100%
    private Long studentElsewhereId; // enrolled in the OTHER teacher's course only

    @BeforeEach
    void seed() {
        User teacherA = teacher("p5-teachera@test.io", "P5TEACHERA", "p5-tok-a");
        User teacherB = teacher("p5-teacherb@test.io", "P5TEACHERB", "p5-tok-b");

        Course course = courses.findByCode("P5JAVA").orElseGet(() -> courses.save(Course.builder()
                .code("P5JAVA").title("P5 Java").price(BigDecimal.TEN).active(true)
                .teacherId(teacherA.getId()).build()));
        courseId = course.getId();

        Course otherCourse = courses.findByCode("P5REACT").orElseGet(() -> courses.save(Course.builder()
                .code("P5REACT").title("P5 React").price(BigDecimal.TEN).active(true)
                .teacherId(teacherB.getId()).build()));
        otherTeacherCourseId = otherCourse.getId();

        CourseModule module = modules.save(CourseModule.builder()
                .course(course).title("Module 1").displayOrder(1).active(true).build());
        CourseLesson lessonOne = lessons.save(CourseLesson.builder()
                .module(module).title("Lesson 1").displayOrder(1).active(true).build());
        CourseLesson lessonTwo = lessons.save(CourseLesson.builder()
                .module(module).title("Lesson 2").displayOrder(2).active(true).build());
        // Inactive lesson must never count toward totals (backend reuses findActiveByCourseId).
        lessons.save(CourseLesson.builder()
                .module(module).title("Retired Lesson").displayOrder(3).active(false).build());

        User studentHalf = student("VITCSTU95101", "P5 Student Half", "p5-half@test.io");
        User studentZero = student("VITCSTU95102", "P5 Student Zero", "p5-zero@test.io");
        User studentFull = student("VITCSTU95103", "P5 Student Full", "p5-full@test.io");
        User studentElsewhere = student("VITCSTU95104", "P5 Student Elsewhere", "p5-else@test.io");
        studentHalfId = studentHalf.getId();
        studentZeroId = studentZero.getId();
        studentFullId = studentFull.getId();
        studentElsewhereId = studentElsewhere.getId();

        enroll(course, studentHalf, EnrollmentStatus.ACTIVE);
        enroll(course, studentZero, EnrollmentStatus.ACTIVE);
        enroll(course, studentFull, EnrollmentStatus.COMPLETED);
        enroll(otherCourse, studentElsewhere, EnrollmentStatus.ACTIVE);

        LocalDateTime watchedAt = LocalDateTime.now().minusHours(2);
        progress.save(StudentLessonProgress.builder()
                .student(studentHalf).lesson(lessonOne).completed(true)
                .progressPercentage(100).lastWatchedAt(watchedAt).build());
        // lessonTwo left untouched for studentHalf -> 1/2 = 50%.

        progress.save(StudentLessonProgress.builder()
                .student(studentFull).lesson(lessonOne).completed(true)
                .progressPercentage(100).lastWatchedAt(watchedAt).build());
        progress.save(StudentLessonProgress.builder()
                .student(studentFull).lesson(lessonTwo).completed(true)
                .progressPercentage(100).lastWatchedAt(watchedAt.plusMinutes(30)).build());
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByUsernameIgnoreCase(username).orElseGet(() -> users.save(User.builder()
                .fullName(username).email(email).username(username).passwordHash("x")
                .role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private User student(String loginId, String fullName, String email) {
        return users.findByStudentLoginIdIgnoreCase(loginId).orElseGet(() -> users.save(User.builder()
                .fullName(fullName).email(email).studentLoginId(loginId)
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
    }

    private void enroll(Course course, User student, EnrollmentStatus status) {
        enrollments.save(Enrollment.builder()
                .studentName(student.getFullName()).email(student.getEmail()).phone("9999999999")
                .course(course).user(student).amount(BigDecimal.TEN).status(status).build());
    }

    private MockHttpServletRequestBuilder asTeacherA(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P5TEACHERA").header(TT, "p5-tok-a");
    }

    private MockHttpServletRequestBuilder asTeacherB(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P5TEACHERB").header(TT, "p5-tok-b");
    }

    /* ---------------- summary: percentage / counts / student / course (items 3, 4, 9) ---------------- */

    @Test
    void courseSummaryReportsCorrectPercentageAndCounts() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95101')].totalLessons").value(2))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95101')].completedLessons").value(1))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95101')].progressPercentage").value(50));
    }

    @Test
    void zeroProgressStudentReportsZeroNotMisleadingData() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95102')].completedLessons").value(0))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95102')].progressPercentage").value(0))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95102')].lastWatchedAt")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void fullyCompletedStudentReportsHundredPercent() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95103')].completedLessons").value(2))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95103')].totalLessons").value(2))
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95103')].progressPercentage").value(100));
    }

    @Test
    void inactiveLessonNeverCountsTowardTotals() throws Exception {
        // Retired (inactive) lesson exists in the module but must not inflate totalLessons past 2.
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTU95103')].totalLessons").value(2));
    }

    /* ---------------- lesson-level detail (items 5, 9) ---------------- */

    @Test
    void studentDetailReportsCorrectLessonAndModuleInfo() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId + "/students/" + studentHalfId + "/progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].moduleTitle").value("Module 1"))
                .andExpect(jsonPath("$.data[0].lessonTitle").value("Lesson 1"))
                .andExpect(jsonPath("$.data[0].completed").value(true))
                .andExpect(jsonPath("$.data[0].lastWatchedAt").exists())
                .andExpect(jsonPath("$.data[1].lessonTitle").value("Lesson 2"))
                .andExpect(jsonPath("$.data[1].completed").value(false))
                .andExpect(jsonPath("$.data[1].lastWatchedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    /* ---------------- unauthorized course rejected (items 6, 9) ---------------- */

    @Test
    void teacherCannotReadAnotherTeachersCourseStudents() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId + "/students")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotReadAnotherTeachersStudentDetailByChangingCourseId() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + otherTeacherCourseId
                        + "/students/" + studentElsewhereId + "/progress")))
                .andExpect(status().isForbidden());
    }

    /* ---------------- unauthorized student rejected (items 6, 9) ---------------- */

    @Test
    void teacherCannotReadAStudentNotEnrolledInThatCourse() throws Exception {
        // studentElsewhere is real and enrolled, but only in Teacher B's course - swapping the
        // studentId into Teacher A's own course must still be rejected.
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + courseId
                        + "/students/" + studentElsewhereId + "/progress")))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherTeacherCannotProbeFirstTeachersCourseOrStudent() throws Exception {
        mvc.perform(asTeacherB(get("/api/v1/teacher/courses/" + courseId + "/students")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacherB(get("/api/v1/teacher/courses/" + courseId
                        + "/students/" + studentHalfId + "/progress")))
                .andExpect(status().isForbidden());
    }

    /* ---------------- empty state: course with no lessons yet (item 8) ---------------- */

    @Test
    void courseWithNoLessonsYetReturnsEmptyDetailNotAnError() throws Exception {
        User teacherC = teacher("p5-teacherc@test.io", "P5TEACHERC", "p5-tok-c");
        Course bareCourse = courses.save(Course.builder()
                .code("P5BARE").title("P5 Bare Course").price(BigDecimal.TEN).active(true)
                .teacherId(teacherC.getId()).build());
        User lonelyStudent = student("VITCSTU95105", "P5 Lonely Student", "p5-lonely@test.io");
        enroll(bareCourse, lonelyStudent, EnrollmentStatus.ACTIVE);

        mvc.perform(get("/api/v1/teacher/courses/" + bareCourse.getId() + "/students/" + lonelyStudent.getId() + "/progress")
                        .header(TU, "P5TEACHERC").header(TT, "p5-tok-c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /* ---------------- unauthenticated / wrong role (item 6) ---------------- */

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses/" + courseId + "/students")).andExpect(status().isUnauthorized());
    }
}

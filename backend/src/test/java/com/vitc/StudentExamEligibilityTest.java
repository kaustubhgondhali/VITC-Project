package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.Enrollment;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.EnrollmentRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentExamEligibilityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private Long courseId;
    private Long lesson1Id;
    private Long lesson2Id;
    private Long lesson3Id;
    private User teacher;
    private User studentA;
    private User studentB;
    private Enrollment enrollmentA;
    private Enrollment enrollmentB;

    @BeforeEach
    void seed() {
        teacher = teacher("teacher-elig@test.io", "TCHELIG", "tch-tok-elig");

        Course course = courses.save(Course.builder()
                .code("ELIG101")
                .title("Exam Eligibility Course")
                .teacherId(teacher.getId())
                .price(BigDecimal.valueOf(999))
                .active(true)
                .build());
        courseId = course.getId();

        CourseModule module = modules.save(CourseModule.builder()
                .course(course)
                .title("Module 1 - Core Fundamentals")
                .displayOrder(1)
                .active(true)
                .build());

        lesson1Id = lessons.save(CourseLesson.builder()
                .module(module)
                .title("Lesson 1: Intro")
                .videoUrl("https://www.youtube.com/embed/lesson1")
                .displayOrder(1)
                .active(true)
                .build()).getId();

        lesson2Id = lessons.save(CourseLesson.builder()
                .module(module)
                .title("Lesson 2: Core Concepts")
                .videoUrl("https://www.youtube.com/embed/lesson2")
                .displayOrder(2)
                .active(true)
                .build()).getId();

        lesson3Id = lessons.save(CourseLesson.builder()
                .module(module)
                .title("Lesson 3: Advanced Topics")
                .videoUrl("https://www.youtube.com/embed/lesson3")
                .displayOrder(3)
                .active(true)
                .build()).getId();

        studentA = student("student-a@test.io", "STUELIGA", "tok-a");
        studentB = student("student-b@test.io", "STUELIGB", "tok-b");

        enrollmentA = enrollments.save(Enrollment.builder()
                .studentName("Student A")
                .email(studentA.getEmail())
                .phone("9999999901")
                .course(course)
                .user(studentA)
                .amount(BigDecimal.valueOf(999))
                .status(EnrollmentStatus.ACTIVE)
                .build());

        enrollmentB = enrollments.save(Enrollment.builder()
                .studentName("Student B")
                .email(studentB.getEmail())
                .phone("9999999902")
                .course(course)
                .user(studentB)
                .amount(BigDecimal.valueOf(999))
                .status(EnrollmentStatus.ACTIVE)
                .build());
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(username).email(email).passwordHash("x").role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE).username(username).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private User student(String email, String loginId, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(loginId).email(email).passwordHash("x").role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE).studentLoginId(loginId).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private void markLessonComplete(String loginId, String token, Long lessonId) throws Exception {
        mvc.perform(post("/api/v1/student/lessons/" + lessonId + "/progress")
                        .header("X-Student-Id", loginId).header("X-Student-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"progressPercentage\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    void studentWithIncompleteLessonsIsNotEligible() throws Exception {
        // 1. Brand new enrollment with 0 completed lessons -> NOT eligible
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 2. Complete 2 out of 3 lessons -> STILL NOT eligible
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);

        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void studentCompletingAllRequiredLessonsBecomesEligible() throws Exception {
        // Complete all 3 lessons for Student A
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        // Now Student A is verified eligible!
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].courseName").value("Exam Eligibility Course"))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));
    }

    @Test
    void refreshDashboardEligibilityRemainsCorrect() throws Exception {
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        // First call
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));

        // Subsequent call (e.g. page refresh)
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));
    }

    @Test
    void differentStudentHasIndependentEligibility() throws Exception {
        // Student A finishes all lessons -> eligible
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        // Student B finishes only 1 lesson -> NOT eligible
        markLessonComplete("STUELIGB", "tok-b", lesson1Id);

        // Student A check
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));

        // Student B check
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void manualEnrollmentStatusDoesNotBypassLessonCompletion() throws Exception {
        // A student whose enrollment is manually updated to COMPLETED without actually finishing lessons is NOT eligible
        Enrollment enrB = enrollments.findFirstByUserIdAndCourseId(studentB.getId(), courseId).orElseThrow();
        enrB.setStatus(EnrollmentStatus.COMPLETED);
        enrollments.save(enrB);

        // 0 lessons completed -> Must NOT be returned as exam eligible
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void eligibleStudentCanApplyForExam() throws Exception {
        // 1. Student A finishes all lessons
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        // 2. Student A applies for exam
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.courseName").value("Exam Eligibility Course"));

        // 3. Application persists on subsequent dashboard queries
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    void ineligibleStudentCannotApplyForExam() throws Exception {
        // Student B has only completed 1 lesson -> Ineligible
        markLessonComplete("STUELIGB", "tok-b", lesson1Id);

        mvc.perform(post("/api/v1/student/exams/" + enrollmentB.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateExamApplicationIsPrevented() throws Exception {
        // 1. Student A completes all lessons and applies
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        // 2. Student A attempts to apply a second time for the same enrollment -> 400 Bad Request
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotApplyForAnotherStudentsEnrollment() throws Exception {
        // Student A completes lessons
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        // Student B tries to apply using Student A's enrollment ID -> 403 Forbidden
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentReceivesScheduledExamInformationWhenTeacherSchedules() throws Exception {
        // 1. Student A completes lessons and applies
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk());

        // 2. Teacher schedules the exam
        String schedulePayload = "{"
                + "\"examDate\":\"" + java.time.LocalDate.now().plusDays(5) + "\","
                + "\"examTime\":\"14:00:00\","
                + "\"mode\":\"Online (Zoom)\","
                + "\"locationOrLink\":\"https://zoom.us/j/123456789\","
                + "\"notes\":\"Bring calculator and student ID.\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollmentA.getId())
                        .header("X-Teacher-Username", "TCHELIG").header("X-Teacher-Token", "tch-tok-elig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isOk());

        // 3. Student queries their exams -> gets SCHEDULED status with all details
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].examDate").value(java.time.LocalDate.now().plusDays(5).toString()))
                .andExpect(jsonPath("$.data[0].examTime").value("14:00:00"))
                .andExpect(jsonPath("$.data[0].mode").value("Online (Zoom)"))
                .andExpect(jsonPath("$.data[0].locationOrLink").value("https://zoom.us/j/123456789"))
                .andExpect(jsonPath("$.data[0].notes").value("Bring calculator and student ID."));
    }

    @Test
    void otherStudentCannotViewDifferentStudentsScheduledExam() throws Exception {
        // Student A has a scheduled exam
        markLessonComplete("STUELIGA", "tok-a", lesson1Id);
        markLessonComplete("STUELIGA", "tok-a", lesson2Id);
        markLessonComplete("STUELIGA", "tok-a", lesson3Id);

        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUELIGA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk());

        String schedulePayload = "{"
                + "\"examDate\":\"" + java.time.LocalDate.now().plusDays(5) + "\","
                + "\"examTime\":\"14:00:00\","
                + "\"mode\":\"Online\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollmentA.getId())
                        .header("X-Teacher-Username", "TCHELIG").header("X-Teacher-Token", "tch-tok-elig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isOk());

        // Student B queries their own exams -> must NOT see Student A's scheduled exam
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUELIGB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}

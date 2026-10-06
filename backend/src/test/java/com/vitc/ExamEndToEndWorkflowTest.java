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
import com.vitc.repository.ExamRepository;
import com.vitc.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class ExamEndToEndWorkflowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired ExamRepository examRepository;

    private User teacher;
    private User studentA;
    private User studentB;
    private Course course;
    private Long lesson1Id;
    private Long lesson2Id;
    private Enrollment enrollmentA;
    private Enrollment enrollmentB;

    @BeforeEach
    void seed() {
        teacher = teacher("e2e-teacher@vitc.edu", "E2ETeacher", "tch-e2e-tok");

        course = courses.save(Course.builder()
                .code("E2E-CS101")
                .title("Complete Full Stack Engineering")
                .teacherId(teacher.getId())
                .price(BigDecimal.valueOf(2499))
                .active(true)
                .build());

        CourseModule module1 = modules.save(CourseModule.builder()
                .course(course)
                .title("Module 1: Backend Architecture")
                .displayOrder(1)
                .active(true)
                .build());

        lesson1Id = lessons.save(CourseLesson.builder()
                .module(module1)
                .title("Lesson 1: Spring Boot Fundamentals")
                .videoUrl("https://youtube.com/embed/e2e1")
                .displayOrder(1)
                .active(true)
                .build()).getId();

        lesson2Id = lessons.save(CourseLesson.builder()
                .module(module1)
                .title("Lesson 2: Database Design & Security")
                .videoUrl("https://youtube.com/embed/e2e2")
                .displayOrder(2)
                .active(true)
                .build()).getId();

        studentA = student("student-alice@vitc.edu", "STUALICE", "tok-alice");
        studentB = student("student-bob@vitc.edu", "STUBOB", "tok-bob");

        enrollmentA = enrollments.save(Enrollment.builder()
                .studentName("Alice Smith")
                .email(studentA.getEmail())
                .phone("9123456780")
                .course(course)
                .user(studentA)
                .amount(BigDecimal.valueOf(2499))
                .status(EnrollmentStatus.ACTIVE)
                .build());

        enrollmentB = enrollments.save(Enrollment.builder()
                .studentName("Bob Johnson")
                .email(studentB.getEmail())
                .phone("9123456781")
                .course(course)
                .user(studentB)
                .amount(BigDecimal.valueOf(2499))
                .status(EnrollmentStatus.ACTIVE)
                .build());
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName("Prof. " + username).email(email).passwordHash("x").role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE).username(username).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        return users.save(u);
    }

    private User student(String email, String loginId, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(loginId).email(email).passwordHash("x").role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE).studentLoginId(loginId).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(2));
        return users.save(u);
    }

    private void markComplete(String loginId, String token, Long lessonId) throws Exception {
        mvc.perform(post("/api/v1/student/lessons/" + lessonId + "/progress")
                        .header("X-Student-Id", loginId).header("X-Student-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"progressPercentage\":100}"))
                .andExpect(status().isOk());
    }

    @Test
    void testCompleteExamWorkflow_Steps1Through5_And_Persistence_And_Security() throws Exception {
        // =========================================================================
        // INITIAL STATE: NOT_ELIGIBLE
        // =========================================================================
        // Student Alice has 0 completed lessons -> Ineligible
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Attempting to apply returns 403 Forbidden
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isForbidden());

        // =========================================================================
        // STEP 1: Student completes every required module/lesson -> ELIGIBLE
        // =========================================================================
        markComplete("STUALICE", "tok-alice", lesson1Id);
        markComplete("STUALICE", "tok-alice", lesson2Id);

        // Student Alice checks exams -> status is ELIGIBLE ("You are eligible for your exam.")
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data[0].courseName").value("Complete Full Stack Engineering"))
                .andExpect(jsonPath("$.data[0].courseCode").value("E2E-CS101"));

        // =========================================================================
        // STEP 2: Student clicks "Apply for Exam" -> APPLIED
        // =========================================================================
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedDate").exists());

        // Student Dashboard query now returns APPLIED ("Exam Application Submitted")
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));

        // Duplicate application is blocked
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isBadRequest());

        // =========================================================================
        // STEP 3: Teacher Admin opens Exam Management -> Application appears in queue
        // =========================================================================
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "E2ETeacher").header("X-Teacher-Token", "tch-e2e-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentName").value("Alice Smith"))
                .andExpect(jsonPath("$.data[0].email").value("student-a@test.io".contains("@") ? "student-alice@vitc.edu" : ""))
                .andExpect(jsonPath("$.data[0].courseName").value("Complete Full Stack Engineering"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));

        // =========================================================================
        // STEP 4: Teacher schedules the exam -> SCHEDULED
        // =========================================================================
        LocalDate scheduledDate = LocalDate.now().plusDays(7);
        String schedulePayload = "{"
                + "\"examDate\":\"" + scheduledDate + "\","
                + "\"examTime\":\"10:00:00\","
                + "\"mode\":\"Online (Google Meet)\","
                + "\"locationOrLink\":\"https://meet.google.com/e2e-vitc-exam\","
                + "\"notes\":\"Please have photo ID ready and join on time.\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollmentA.getId())
                        .header("X-Teacher-Username", "E2ETeacher").header("X-Teacher-Token", "tch-e2e-tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.examDate").value(scheduledDate.toString()))
                .andExpect(jsonPath("$.data.examTime").value("10:00:00"))
                .andExpect(jsonPath("$.data.mode").value("Online (Google Meet)"))
                .andExpect(jsonPath("$.data.locationOrLink").value("https://meet.google.com/e2e-vitc-exam"))
                .andExpect(jsonPath("$.data.notes").value("Please have photo ID ready and join on time."));

        // =========================================================================
        // STEP 5: Student opens Student Dashboard -> sees scheduled exam details
        // =========================================================================
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].examDate").value(scheduledDate.toString()))
                .andExpect(jsonPath("$.data[0].examTime").value("10:00:00"))
                .andExpect(jsonPath("$.data[0].mode").value("Online (Google Meet)"))
                .andExpect(jsonPath("$.data[0].locationOrLink").value("https://meet.google.com/e2e-vitc-exam"))
                .andExpect(jsonPath("$.data[0].notes").value("Please have photo ID ready and join on time."));

        // =========================================================================
        // DATA PERSISTENCE CHECK: Page refresh / re-login
        // =========================================================================
        // Re-query student exams (simulating dashboard reload)
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUALICE").header("X-Student-Token", "tok-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].examDate").value(scheduledDate.toString()));

        // Re-query teacher queue (simulating teacher exam management reload)
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "E2ETeacher").header("X-Teacher-Token", "tch-e2e-tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"));

        // =========================================================================
        // SECURITY & ISOLATION CHECK: Student B must NOT see Alice's exam
        // =========================================================================
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STUBOB").header("X-Student-Token", "tok-bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Student Bob cannot apply for Alice's enrollment
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA.getId() + "/apply")
                        .header("X-Student-Id", "STUBOB").header("X-Student-Token", "tok-bob"))
                .andExpect(status().isForbidden());
    }
}


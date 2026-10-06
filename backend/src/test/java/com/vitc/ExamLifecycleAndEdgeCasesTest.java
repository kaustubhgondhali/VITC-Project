package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.Enrollment;
import com.vitc.entity.Exam;
import com.vitc.entity.User;
import com.vitc.entity.enums.EnrollmentStatus;
import com.vitc.entity.enums.ExamStatus;
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
class ExamLifecycleAndEdgeCasesTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired ExamRepository examRepository;

    private User teacherA;
    private User teacherB;
    private User student1;
    private User student2;
    private Course courseA;
    private Course courseB;
    private Long lessonA1Id;
    private Long lessonA2Id;
    private Long lessonB1Id;
    private Enrollment enrollmentA1;
    private Enrollment enrollmentB1;
    private Enrollment enrollmentA2;

    @BeforeEach
    void seed() {
        teacherA = teacher("teacher.a@test.io", "TCHA", "tch-tok-a");
        teacherB = teacher("teacher.b@test.io", "TCHB", "tch-tok-b");

        courseA = courses.save(Course.builder()
                .code("CRSA")
                .title("Course Alpha")
                .teacherId(teacherA.getId())
                .price(BigDecimal.valueOf(1000))
                .active(true)
                .build());

        courseB = courses.save(Course.builder()
                .code("CRSB")
                .title("Course Beta")
                .teacherId(teacherB.getId())
                .price(BigDecimal.valueOf(1500))
                .active(true)
                .build());

        CourseModule mA = modules.save(CourseModule.builder()
                .course(courseA).title("Mod A").displayOrder(1).active(true).build());
        lessonA1Id = lessons.save(CourseLesson.builder()
                .module(mA).title("Lesson A1").videoUrl("https://youtube.com/a1").displayOrder(1).active(true).build()).getId();
        lessonA2Id = lessons.save(CourseLesson.builder()
                .module(mA).title("Lesson A2").videoUrl("https://youtube.com/a2").displayOrder(2).active(true).build()).getId();

        CourseModule mB = modules.save(CourseModule.builder()
                .course(courseB).title("Mod B").displayOrder(1).active(true).build());
        lessonB1Id = lessons.save(CourseLesson.builder()
                .module(mB).title("Lesson B1").videoUrl("https://youtube.com/b1").displayOrder(1).active(true).build()).getId();

        student1 = student("student1@test.io", "STU1", "stu-tok-1");
        student2 = student("student2@test.io", "STU2", "stu-tok-2");

        enrollmentA1 = enrollments.save(Enrollment.builder()
                .studentName("Student One").email(student1.getEmail()).phone("1111111111")
                .course(courseA).user(student1).amount(BigDecimal.valueOf(1000)).status(EnrollmentStatus.ACTIVE).build());

        enrollmentB1 = enrollments.save(Enrollment.builder()
                .studentName("Student One").email(student1.getEmail()).phone("1111111111")
                .course(courseB).user(student1).amount(BigDecimal.valueOf(1500)).status(EnrollmentStatus.ACTIVE).build());

        enrollmentA2 = enrollments.save(Enrollment.builder()
                .studentName("Student Two").email(student2.getEmail()).phone("2222222222")
                .course(courseA).user(student2).amount(BigDecimal.valueOf(1000)).status(EnrollmentStatus.ACTIVE).build());
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(username).email(email).passwordHash("x").role(UserRole.TEACHER)
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
    void edgeCase_fullExamLifecycleFlow() throws Exception {
        // State 1: NOT_ELIGIBLE (0 of 2 lessons)
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(post("/api/v1/student/exams/" + enrollmentA1.getId() + "/apply")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isForbidden());

        // State 2: ELIGIBLE (Completes both lessons in Course A)
        markComplete("STU1", "stu-tok-1", lessonA1Id);
        markComplete("STU1", "stu-tok-1", lessonA2Id);

        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ELIGIBLE"));

        // State 3: APPLIED
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA1.getId() + "/apply")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        // Duplicate apply blocked
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA1.getId() + "/apply")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isBadRequest());

        // Teacher A sees application
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCHA").header("X-Teacher-Token", "tch-tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));

        // State 4: SCHEDULED
        String scheduleJson = "{"
                + "\"examDate\":\"" + LocalDate.now().plusDays(4) + "\","
                + "\"examTime\":\"15:30:00\","
                + "\"mode\":\"Online (Meet)\","
                + "\"locationOrLink\":\"https://meet.google.com/xyz\","
                + "\"notes\":\"Bring calculator.\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollmentA1.getId())
                        .header("X-Teacher-Username", "TCHA").header("X-Teacher-Token", "tch-tok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        // Student 1 sees scheduled details
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].locationOrLink").value("https://meet.google.com/xyz"));

        // State 5: COMPLETED
        Exam exam = examRepository.findByEnrollmentId(enrollmentA1.getId()).orElseThrow();
        exam.setStatus(ExamStatus.COMPLETED);
        examRepository.save(exam);

        // Student and Teacher still receive completed exam record
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));

        // Applying on completed exam is rejected
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA1.getId() + "/apply")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void edgeCase_crossStudentAndCrossCourseIsolation() throws Exception {
        // Student 1 finishes Course A
        markComplete("STU1", "stu-tok-1", lessonA1Id);
        markComplete("STU1", "stu-tok-1", lessonA2Id);

        // Student 1 applies for Course A
        mvc.perform(post("/api/v1/student/exams/" + enrollmentA1.getId() + "/apply")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk());

        // Student 1 also has Course B (incomplete) -> Course B must NOT show up as eligible
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseCode").value("CRSA"));

        // Student 2 (enrolled in Course A but 0 lessons complete) -> 0 exams
        mvc.perform(get("/api/v1/student/exams")
                        .header("X-Student-Id", "STU2").header("X-Student-Token", "stu-tok-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Teacher B (assigned to Course B) must NOT see Student 1's Course A application
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCHB").header("X-Teacher-Token", "tch-tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}


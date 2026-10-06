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
class TeacherExamApplicationsTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private User teacher1;
    private User teacher2;
    private User student1;
    private User student2;
    private Course course1;
    private Course course2;
    private Long lesson1Id;
    private Long lesson2Id;
    private Enrollment enrollment1;
    private Enrollment enrollment2;

    @BeforeEach
    void seed() {
        teacher1 = teacher("teacher1@test.io", "TCH1", "tch-tok-1");
        teacher2 = teacher("teacher2@test.io", "TCH2", "tch-tok-2");

        course1 = courses.save(Course.builder()
                .code("TCH-EXAM1")
                .title("Teacher Exam Course 1")
                .teacherId(teacher1.getId())
                .price(BigDecimal.valueOf(1200))
                .active(true)
                .build());

        course2 = courses.save(Course.builder()
                .code("TCH-EXAM2")
                .title("Teacher Exam Course 2")
                .teacherId(teacher2.getId())
                .price(BigDecimal.valueOf(1500))
                .active(true)
                .build());

        CourseModule m1 = modules.save(CourseModule.builder()
                .course(course1)
                .title("Module 1")
                .displayOrder(1)
                .active(true)
                .build());

        lesson1Id = lessons.save(CourseLesson.builder()
                .module(m1)
                .title("Lesson 1")
                .videoUrl("https://www.youtube.com/embed/l1")
                .displayOrder(1)
                .active(true)
                .build()).getId();

        lesson2Id = lessons.save(CourseLesson.builder()
                .module(m1)
                .title("Lesson 2")
                .videoUrl("https://www.youtube.com/embed/l2")
                .displayOrder(2)
                .active(true)
                .build()).getId();

        student1 = student("student1@test.io", "STUEXAM1", "stu-tok-1");
        student2 = student("student2@test.io", "STUEXAM2", "stu-tok-2");

        enrollment1 = enrollments.save(Enrollment.builder()
                .studentName("Student One")
                .email(student1.getEmail())
                .phone("9876543210")
                .course(course1)
                .user(student1)
                .amount(BigDecimal.valueOf(1200))
                .status(EnrollmentStatus.ACTIVE)
                .build());

        enrollment2 = enrollments.save(Enrollment.builder()
                .studentName("Student Two")
                .email(student2.getEmail())
                .phone("9876543211")
                .course(course1)
                .user(student2)
                .amount(BigDecimal.valueOf(1200))
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

    private void completeAllLessons(String studentLoginId, String token) throws Exception {
        mvc.perform(post("/api/v1/student/lessons/" + lesson1Id + "/progress")
                        .header("X-Student-Id", studentLoginId).header("X-Student-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"progressPercentage\":100}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/student/lessons/" + lesson2Id + "/progress")
                        .header("X-Student-Id", studentLoginId).header("X-Student-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"progressPercentage\":100}"))
                .andExpect(status().isOk());
    }

    @Test
    void studentApplicationAppearsInTeacherAdminWithAppliedStatus() throws Exception {
        // 1. Complete lessons and submit application as student
        completeAllLessons("STUEXAM1", "stu-tok-1");

        mvc.perform(post("/api/v1/student/exams/" + enrollment1.getId() + "/apply")
                        .header("X-Student-Id", "STUEXAM1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"));

        // 2. Teacher 1 checks exam-ready students / applications
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentName").value("Student One"))
                .andExpect(jsonPath("$.data[0].email").value("student1@test.io"))
                .andExpect(jsonPath("$.data[0].courseName").value("Teacher Exam Course 1"))
                .andExpect(jsonPath("$.data[0].courseCode").value("TCH-EXAM1"))
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    void unassignedTeacherDoesNotSeeOtherTeachersApplications() throws Exception {
        completeAllLessons("STUEXAM1", "stu-tok-1");

        mvc.perform(post("/api/v1/student/exams/" + enrollment1.getId() + "/apply")
                        .header("X-Student-Id", "STUEXAM1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk());

        // Teacher 2 (not assigned to Course 1) checks applications -> should be empty
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCH2").header("X-Teacher-Token", "tch-tok-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void applicationPersistsOnTeacherRefresh() throws Exception {
        completeAllLessons("STUEXAM1", "stu-tok-1");

        mvc.perform(post("/api/v1/student/exams/" + enrollment1.getId() + "/apply")
                        .header("X-Student-Id", "STUEXAM1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk());

        // Call 1
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));

        // Call 2 (simulating page reload)
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("APPLIED"));
    }

    @Test
    void teacherCanScheduleExamForAppliedStudent() throws Exception {
        // 1. Student applies
        completeAllLessons("STUEXAM1", "stu-tok-1");

        mvc.perform(post("/api/v1/student/exams/" + enrollment1.getId() + "/apply")
                        .header("X-Student-Id", "STUEXAM1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk());

        // 2. Teacher schedules the exam
        String schedulePayload = "{"
                + "\"examDate\":\"" + LocalDate.now().plusDays(3) + "\","
                + "\"examTime\":\"11:30:00\","
                + "\"mode\":\"Online\","
                + "\"locationOrLink\":\"https://meet.google.com/abc-def-ghi\","
                + "\"notes\":\"Please bring ID card and arrive 10 min early.\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollment1.getId())
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.examDate").value(LocalDate.now().plusDays(3).toString()))
                .andExpect(jsonPath("$.data.mode").value("Online"))
                .andExpect(jsonPath("$.data.locationOrLink").value("https://meet.google.com/abc-def-ghi"));

        // 3. Status persists as SCHEDULED in teacher's list
        mvc.perform(get("/api/v1/teacher/exam-ready-students")
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].locationOrLink").value("https://meet.google.com/abc-def-ghi"));
    }

    @Test
    void teacherCannotScheduleExamForOtherTeachersCourse() throws Exception {
        completeAllLessons("STUEXAM1", "stu-tok-1");

        mvc.perform(post("/api/v1/student/exams/" + enrollment1.getId() + "/apply")
                        .header("X-Student-Id", "STUEXAM1").header("X-Student-Token", "stu-tok-1"))
                .andExpect(status().isOk());

        String schedulePayload = "{"
                + "\"examDate\":\"" + LocalDate.now().plusDays(3) + "\","
                + "\"examTime\":\"11:30:00\","
                + "\"mode\":\"Online\""
                + "}";

        // Teacher 2 tries to schedule exam for Teacher 1's course -> 403 Forbidden
        mvc.perform(post("/api/v1/teacher/exams/" + enrollment1.getId())
                        .header("X-Teacher-Username", "TCH2").header("X-Teacher-Token", "tch-tok-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotScheduleExamForIncompleteStudent() throws Exception {
        // Student 2 has not completed lessons -> 403 Forbidden
        String schedulePayload = "{"
                + "\"examDate\":\"" + LocalDate.now().plusDays(3) + "\","
                + "\"examTime\":\"11:30:00\","
                + "\"mode\":\"Online\""
                + "}";

        mvc.perform(post("/api/v1/teacher/exams/" + enrollment2.getId())
                        .header("X-Teacher-Username", "TCH1").header("X-Teacher-Token", "tch-tok-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schedulePayload))
                .andExpect(status().isForbidden());
    }
}

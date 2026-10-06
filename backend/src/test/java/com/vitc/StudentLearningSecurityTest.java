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

/** Part 2 authorisation tests: enrolment-based course + lesson access. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // each test rolls back so the shared in-memory DB stays clean
class StudentLearningSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired EnrollmentRepository enrollments;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private Long javaId;
    private Long pythonId;
    private Long javaLessonId;
    private Long pythonLessonId;

    @BeforeEach
    void seed() {
        Course java = course("TJAVA", "Test Java");
        Course python = course("TPY", "Test Python");
        javaId = java.getId();
        pythonId = python.getId();
        javaLessonId = lesson(java, "Java Variables");
        pythonLessonId = lesson(python, "Python Variables");

        User a = student("stu-a@test.io", "VITCTESTA", "tok-a");
        student("stu-b@test.io", "VITCTESTB", "tok-b");
        if (enrollments.findFirstByUserIdAndCourseId(a.getId(), javaId).isEmpty()) {
            enrollments.save(Enrollment.builder().studentName("A").email(a.getEmail()).phone("-")
                    .course(java).user(a).amount(BigDecimal.ONE).status(EnrollmentStatus.ACTIVE).build());
        }
    }

    private Course course(String code, String title) {
        return courses.findAll().stream().filter(c -> code.equals(c.getCode())).findFirst()
                .orElseGet(() -> courses.save(Course.builder().code(code).title(title)
                        .price(BigDecimal.TEN).active(true).build()));
    }

    private Long lesson(Course c, String title) {
        CourseModule m = modules.findByCourseIdOrderByDisplayOrderAscIdAsc(c.getId()).stream().findFirst()
                .orElseGet(() -> modules.save(CourseModule.builder().course(c).title("M1").displayOrder(1).active(true).build()));
        return lessons.findByModuleIdAndActiveTrueOrderByDisplayOrderAscIdAsc(m.getId()).stream().findFirst()
                .orElseGet(() -> lessons.save(CourseLesson.builder().module(m).title(title)
                        .videoUrl("https://www.youtube.com/embed/private").displayOrder(1).active(true).build()))
                .getId();
    }

    private User student(String email, String loginId, String token) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(loginId).email(email).passwordHash("x").role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE).studentLoginId(loginId).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    /* Test 1: owner can open the course + its lesson video. */
    @Test
    void ownedCourseIsAccessible() throws Exception {
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(javaId));
        mvc.perform(get("/api/v1/student/lessons/" + javaLessonId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").exists());
    }

    /* Test 2/3: another course, another student's lesson -> 403, no content. */
    @Test
    void foreignCourseAndLessonAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/student/courses/" + pythonId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/lessons/" + pythonLessonId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.videoUrl").doesNotExist());
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveCourseModuleAndLessonAreHidden() throws Exception {
        Course java = courses.findById(javaId).orElseThrow();
        java.setActive(false);
        courses.save(java);

        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/courses")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        java.setActive(true);
        courses.save(java);
        CourseModule inactiveModule = modules.save(CourseModule.builder().course(java)
                .title("Inactive Module").displayOrder(9).active(false).build());
        CourseLesson inactiveLesson = lessons.save(CourseLesson.builder().module(inactiveModule)
                .title("Inactive Lesson").videoUrl("https://example.test/inactive.mp4")
                .displayOrder(1).active(false).build());

        mvc.perform(get("/api/v1/student/lessons/" + inactiveLesson.getId())
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/courses/" + javaId + "/modules")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title").value(org.hamcrest.Matchers.not("Inactive Module")));

        CourseModule publishedModule = modules.findByCourseIdAndActiveTrueOrderByDisplayOrderAscIdAsc(javaId)
                .stream().findFirst().orElseThrow();
        CourseLesson mediaLessLesson = lessons.save(CourseLesson.builder().module(publishedModule)
                .title("Not Yet Uploaded").displayOrder(99).active(true).build());
        mvc.perform(get("/api/v1/student/lessons/" + mediaLessLesson.getId())
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isForbidden());
    }

    /* Test 4: no session at all. */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/lessons/" + javaLessonId)).andExpect(status().isUnauthorized());
    }

    /* Test 5: after logout the token no longer works. */
    @Test
    void logoutRevokesAccess() throws Exception {
        mvc.perform(post("/api/v1/student/auth/logout")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isUnauthorized());
    }

    /* Test 6: a second enrolment shows up on the same account. */
    @Test
    void secondEnrollmentAddsSecondCourse() throws Exception {
        mvc.perform(get("/api/v1/student/courses")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        User a = users.findByStudentLoginIdIgnoreCase("VITCTESTA").orElseThrow();
        enrollments.save(Enrollment.builder().studentName("A").email(a.getEmail()).phone("-")
                .course(courses.findById(pythonId).orElseThrow()).user(a).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());
        mvc.perform(get("/api/v1/student/courses")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/student/courses/" + pythonId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk());
    }

    /* Progress: mark complete moves the course percentage. */
    @Test
    void progressIsTracked() throws Exception {
        mvc.perform(post("/api/v1/student/lessons/" + javaLessonId + "/progress")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.courseProgressPercentage").value(100));
        mvc.perform(post("/api/v1/student/lessons/" + pythonLessonId + "/progress")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void progressCanBeExplicitlyUnmarked() throws Exception {
        String authStudent = "VITCTESTA";
        String authToken = "tok-a";
        mvc.perform(post("/api/v1/student/lessons/" + javaLessonId + "/progress")
                        .header("X-Student-Id", authStudent).header("X-Student-Token", authToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        mvc.perform(post("/api/v1/student/lessons/" + javaLessonId + "/progress")
                        .header("X-Student-Id", authStudent).header("X-Student-Token", authToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.progressPercentage").value(0))
                .andExpect(jsonPath("$.data.courseProgressPercentage").value(0));
    }

    /* PART 4: completing every lesson in a course flips the enrolment itself
       to COMPLETED (single-lesson seed course, so one call finishes it). */
    @Test
    void completingAllLessonsMarksCourseCompleted() throws Exception {
        mvc.perform(post("/api/v1/student/lessons/" + javaLessonId + "/progress")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progressPercentage").value(100));
    }

    /* PART 4 security: two students enrolled in the SAME course each get
       their own progress row - one student's completion never touches, nor
       is visible through, another student's progress on the same lesson. */
    @Test
    void progressIsIsolatedBetweenStudentsOnTheSameCourse() throws Exception {
        User b = users.findByStudentLoginIdIgnoreCase("VITCTESTB").orElseThrow();
        enrollments.save(Enrollment.builder().studentName("B").email(b.getEmail()).phone("-")
                .course(courses.findById(javaId).orElseThrow()).user(b).amount(BigDecimal.ONE)
                .status(EnrollmentStatus.ACTIVE).build());

        mvc.perform(post("/api/v1/student/lessons/" + javaLessonId + "/progress")
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isOk());

        // Student B's own view of the same lesson is still un-started.
        mvc.perform(get("/api/v1/student/lessons/" + javaLessonId)
                        .header("X-Student-Id", "VITCTESTB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false));

        // Student A's enrolment completed; student B's own enrolment did not.
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTA").header("X-Student-Token", "tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/student/courses/" + javaId)
                        .header("X-Student-Id", "VITCTESTB").header("X-Student-Token", "tok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentStatus").value("ACTIVE"));
    }
}

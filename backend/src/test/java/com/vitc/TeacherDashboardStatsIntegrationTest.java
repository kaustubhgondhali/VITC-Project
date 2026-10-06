package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 8C: end-to-end check that {@code GET /api/v1/teacher/dashboard/stats} reflects real
 * module/lesson/video/enrollment data - not just that the numbers are non-negative - and that two
 * teachers' data never mixes.
 *
 * <p>Fixture for Teacher A:</p>
 * <ul>
 *   <li>Course 1 ("Test Stats Java"): Module 1 has 2 lessons (1 with a video, 1 without),
 *       Module 2 has 1 lesson (with a video) -&gt; 2 modules, 3 lessons, 2 videos.</li>
 *   <li>Course 2 ("Test Stats DevOps"): 1 module, 1 lesson (no video) -&gt; 1 module, 1 lesson, 0 videos.</li>
 *   <li>Totals for Teacher A: 2 courses, 3 modules, 4 lessons, 2 videos.</li>
 *   <li>Enrollments: two different students ACTIVE on Course 1 (2 distinct active students),
 *       the same student ACTIVE again on Course 2 (must not double count),
 *       plus one PENDING enrollment on Course 1 (must not count) -&gt; 2 active students.</li>
 * </ul>
 * <p>Teacher B owns a separate course with its own modules/lessons/enrollments, used only to prove
 * none of it leaks into Teacher A's numbers.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherDashboardStatsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;

    @BeforeEach
    void seed() {
        User teacherA = teacher("stats-a@test.io", "STATSTEACHERA", "stok-a");
        User teacherB = teacher("stats-b@test.io", "STATSTEACHERB", "stok-b");

        /* ---- Teacher A: two courses ---- */
        Course course1 = course("STJAVA", "Test Stats Java", teacherA.getId());
        CourseModule c1m1 = module(course1, "Module 1", 1);
        lesson(c1m1, "Lesson 1a", "https://video/1a", 1);
        lesson(c1m1, "Lesson 1b", null, 2);
        CourseModule c1m2 = module(course1, "Module 2", 2);
        lesson(c1m2, "Lesson 2a", "https://video/2a", 1);

        Course course2 = course("STDEVOPS", "Test Stats DevOps", teacherA.getId());
        CourseModule c2m1 = module(course2, "Module 1", 1);
        lesson(c2m1, "Lesson 1", null, 1);

        enroll(course1, "stu1@test.io", EnrollmentStatus.ACTIVE);
        enroll(course1, "stu2@test.io", EnrollmentStatus.ACTIVE);
        enroll(course1, "stu3@test.io", EnrollmentStatus.PENDING); // must not count
        enroll(course2, "stu1@test.io", EnrollmentStatus.ACTIVE); // same student, different course -> no double count

        /* ---- Teacher B: unrelated course, must never affect Teacher A's numbers ---- */
        Course courseB = course("STPY", "Test Stats Python", teacherB.getId());
        CourseModule bm1 = module(courseB, "B Module 1", 1);
        lesson(bm1, "B Lesson 1", "https://video/b1", 1);
        lesson(bm1, "B Lesson 2", "https://video/b2", 2);
        enroll(courseB, "stub1@test.io", EnrollmentStatus.ACTIVE);
        enroll(courseB, "stub2@test.io", EnrollmentStatus.ACTIVE);
    }

    private User teacher(String email, String username, String token) {
        User u = users.findByUsernameIgnoreCase(username).orElseGet(() -> users.save(User.builder()
                .fullName(username).email(email).username(username).passwordHash("x")
                .role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(u);
    }

    private Course course(String code, String title, Long teacherId) {
        return courses.findByCode(code).orElseGet(() -> courses.save(Course.builder()
                .code(code).title(title).price(BigDecimal.TEN).active(true).teacherId(teacherId).build()));
    }

    private CourseModule module(Course c, String title, int order) {
        return modules.save(CourseModule.builder().course(c).title(title).displayOrder(order).active(true).build());
    }

    private void lesson(CourseModule m, String title, String videoUrl, int order) {
        lessons.save(CourseLesson.builder().module(m).title(title).videoUrl(videoUrl)
                .displayOrder(order).active(true).build());
    }

    private void enroll(Course c, String email, EnrollmentStatus status) {
        enrollments.save(Enrollment.builder().studentName(email).email(email).phone("-")
                .course(c).amount(BigDecimal.ONE).status(status).build());
    }

    @Test
    void teacherASeesOnlyTheirOwnComputedStats() throws Exception {
        mvc.perform(get("/api/v1/teacher/dashboard/stats")
                        .header("X-Teacher-Username", "STATSTEACHERA").header("X-Teacher-Token", "stok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myCourses").value(2))
                .andExpect(jsonPath("$.data.totalModules").value(3))
                .andExpect(jsonPath("$.data.totalLessons").value(4))
                .andExpect(jsonPath("$.data.totalVideos").value(2))
                .andExpect(jsonPath("$.data.activeStudents").value(2))
                // PART 1/8 (Phase 2): both of Teacher A's courses are published (active=true);
                // total enrolled students counts stu1/stu2/stu3 once each, regardless of status
                // (unlike activeStudents, which excludes the PENDING stu3).
                .andExpect(jsonPath("$.data.publishedCourses").value(2))
                .andExpect(jsonPath("$.data.draftCourses").value(0))
                .andExpect(jsonPath("$.data.totalEnrolledStudents").value(3));
    }

    @Test
    void teacherBSeesOnlyTheirOwnComputedStatsNeverTeacherAs() throws Exception {
        mvc.perform(get("/api/v1/teacher/dashboard/stats")
                        .header("X-Teacher-Username", "STATSTEACHERB").header("X-Teacher-Token", "stok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myCourses").value(1))
                .andExpect(jsonPath("$.data.totalModules").value(1))
                .andExpect(jsonPath("$.data.totalLessons").value(2))
                .andExpect(jsonPath("$.data.totalVideos").value(2))
                .andExpect(jsonPath("$.data.activeStudents").value(2))
                .andExpect(jsonPath("$.data.publishedCourses").value(1))
                .andExpect(jsonPath("$.data.draftCourses").value(0))
                .andExpect(jsonPath("$.data.totalEnrolledStudents").value(2));
    }

    /* The stats endpoint takes no id from the client at all - the teacher is entirely resolved
       from the session - so there is nothing for Teacher A to tamper with to see Teacher B's
       numbers. This test simply confirms an unauthenticated caller gets nothing back either. */
    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/teacher/dashboard/stats")).andExpect(status().isUnauthorized());
    }
}

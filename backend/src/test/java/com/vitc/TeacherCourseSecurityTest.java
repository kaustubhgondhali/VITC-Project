package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseRepository;
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
 * PART 8B: Teacher "My Courses" authorisation tests.
 * <p>Verifies, with two teacher/course assignment scenarios, that:</p>
 * <ul>
 *   <li>a teacher's assigned course shows up in {@code GET /teacher/courses};</li>
 *   <li>an unassigned/foreign course never shows up;</li>
 *   <li>manually requesting an unassigned course by id ({@code GET /teacher/courses/{id}})
 *       is rejected server-side (403), even though the id itself is a real course.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherCourseSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    private Long javaId;
    private Long pythonId;

    @BeforeEach
    void seed() {
        User teacherA = teacher("teacher-a@test.io", "TTEACHERA", "ttok-a");
        User teacherB = teacher("teacher-b@test.io", "TTEACHERB", "ttok-b");

        Course java = course("TTJAVA", "Test Teacher Java", teacherA.getId());
        Course python = course("TTPY", "Test Teacher Python", teacherB.getId());
        javaId = java.getId();
        pythonId = python.getId();
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

    /* Scenario 1: Teacher A sees only their own assigned course. */
    @Test
    void assignedCourseAppearsForOwningTeacher() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses")
                        .header("X-Teacher-Username", "TTEACHERA").header("X-Teacher-Token", "ttok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(javaId));

        mvc.perform(get("/api/v1/teacher/courses/" + javaId)
                        .header("X-Teacher-Username", "TTEACHERA").header("X-Teacher-Token", "ttok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(javaId));
    }

    /* Scenario 2: Teacher A cannot see or open Teacher B's course, even by editing the id. */
    @Test
    void unassignedCourseIsHiddenAndDirectAccessIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses")
                        .header("X-Teacher-Username", "TTEACHERA").header("X-Teacher-Token", "ttok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(javaId));

        mvc.perform(get("/api/v1/teacher/courses/" + pythonId)
                        .header("X-Teacher-Username", "TTEACHERA").header("X-Teacher-Token", "ttok-a"))
                .andExpect(status().isForbidden());
    }

    /* No session at all -> rejected before it ever reaches the controller. */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/teacher/courses/" + javaId)).andExpect(status().isUnauthorized());
    }

    /* A non-existent course id is a 404, not a 403 (no assigned-course info is leaked either way). */
    @Test
    void nonExistentCourseIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses/999999")
                        .header("X-Teacher-Username", "TTEACHERA").header("X-Teacher-Token", "ttok-a"))
                .andExpect(status().isNotFound());
    }
}

package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 3/8 - Teacher "Course Information" edit tests.
 *
 * <p>Covers the minimum compatible course-edit surface added in this part: a Teacher can update
 * the title/description of their own assigned course, business fields (code, price, category,
 * active) are left untouched, and the same "not found vs not yours" authorisation chain used by
 * every other Teacher content endpoint is enforced here too.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherCourseInfoEditTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;

    private Long courseA;
    private Long courseB;

    @BeforeEach
    void seed() {
        User a = teacher("ci-a@test.io", "CITEACHERA", "citok-a");
        User b = teacher("ci-b@test.io", "CITEACHERB", "citok-b");
        courseA = course("CIJAVA", "CI Test Java", a.getId()).getId();
        courseB = course("CIPY", "CI Test Python", b.getId()).getId();
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
                .code(code).title(title).price(BigDecimal.TEN).category("Programming")
                .active(true).teacherId(teacherId).build()));
    }

    @Test
    void teacherCanEditTitleAndDescriptionOfOwnCourse() throws Exception {
        mvc.perform(put("/api/v1/teacher/courses/" + courseA)
                        .header("X-Teacher-Username", "CITEACHERA").header("X-Teacher-Token", "citok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"CI Test Java - Updated\",\"description\":\"New description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("CI Test Java - Updated"))
                .andExpect(jsonPath("$.data.description").value("New description"))
                // Business fields must survive the edit unchanged.
                .andExpect(jsonPath("$.data.code").value("CIJAVA"))
                .andExpect(jsonPath("$.data.category").value("Programming"));
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        mvc.perform(put("/api/v1/teacher/courses/" + courseA)
                        .header("X-Teacher-Username", "CITEACHERA").header("X-Teacher-Token", "citok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherCannotEditAnotherTeachersCourse() throws Exception {
        mvc.perform(put("/api/v1/teacher/courses/" + courseB)
                        .header("X-Teacher-Username", "CITEACHERA").header("X-Teacher-Token", "citok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\",\"description\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Confirm the row was genuinely untouched.
        mvc.perform(put("/api/v1/teacher/courses/" + courseB)
                        .header("X-Teacher-Username", "CITEACHERB").header("X-Teacher-Token", "citok-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"CI Test Python\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("CI Test Python"));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(put("/api/v1/teacher/courses/" + courseA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":null}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonExistentCourseIsNotFound() throws Exception {
        mvc.perform(put("/api/v1/teacher/courses/999999")
                        .header("X-Teacher-Username", "CITEACHERA").header("X-Teacher-Token", "citok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"description\":null}"))
                .andExpect(status().isNotFound());
    }
}

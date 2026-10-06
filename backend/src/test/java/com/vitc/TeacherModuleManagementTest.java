package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseModuleRepository;
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
 * PART 9A: Teacher module management + authorisation tests.
 *
 * <p>Proves the full chain authenticated teacher -&gt; assigned course -&gt; module: teacher A can
 * list/add/edit/reorder/activate/deactivate modules on their own course, and every one of those
 * operations is rejected (403) when aimed at teacher B's course or module.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherModuleManagementTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;

    private Long courseA;
    private Long courseB;
    private Long moduleB;

    @BeforeEach
    void seed() {
        User a = teacher("mod-a@test.io", "MODTEACHERA", "mtok-a");
        User b = teacher("mod-b@test.io", "MODTEACHERB", "mtok-b");
        courseA = course("MODA", "Module Test Java", a.getId()).getId();
        Course cb = course("MODB", "Module Test Python", b.getId());
        courseB = cb.getId();
        moduleB = modules.save(CourseModule.builder()
                .course(cb).title("B module").displayOrder(1).active(true).build()).getId();
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

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";

    @Test
    void teacherCanManageModulesOfAssignedCourse() throws Exception {
        // add two modules
        String first = mvc.perform(post("/api/v1/teacher/courses/" + courseA + "/modules")
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Fundamentals\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(1))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        Long m1 = Long.valueOf(first.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/v1/teacher/courses/" + courseA + "/modules")
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"OOP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(2));

        // edit
        mvc.perform(put("/api/v1/teacher/modules/" + m1)
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Basics\",\"description\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Java Basics"));

        // reorder down
        mvc.perform(patch("/api/v1/teacher/modules/" + m1 + "/move?direction=1")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(2));

        // deactivate + activate
        mvc.perform(patch("/api/v1/teacher/modules/" + m1 + "/status?active=false")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(patch("/api/v1/teacher/modules/" + m1 + "/status?active=true")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // tree
        mvc.perform(get("/api/v1/teacher/courses/" + courseA + "/content")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules.length()").value(2));
    }

    @Test
    void teacherCannotTouchAnotherTeachersCourseOrModule() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses/" + courseB + "/content")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/teacher/courses/" + courseB + "/modules")
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/teacher/modules/" + moduleB)
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/teacher/modules/" + moduleB + "/move?direction=1")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/teacher/modules/" + moduleB + "/status?active=false")
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isForbidden());

        // unauthenticated
        mvc.perform(get("/api/v1/teacher/courses/" + courseA + "/content"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCanViewAndDeleteOwnModule() throws Exception {
        String created = mvc.perform(post("/api/v1/teacher/courses/" + courseA + "/modules")
                        .header(U, "MODTEACHERA").header(T, "mtok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Collections\",\"description\":\"Lists and maps\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // view a single module
        mvc.perform(get("/api/v1/teacher/modules/" + id)
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.courseId").value(courseA))
                .andExpect(jsonPath("$.data.title").value("Collections"))
                .andExpect(jsonPath("$.data.lessons.length()").value(0));

        // delete it
        mvc.perform(delete("/api/v1/teacher/modules/" + id)
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/teacher/modules/" + id)
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotViewOrDeleteAnotherTeachersModule() throws Exception {
        mvc.perform(get("/api/v1/teacher/modules/" + moduleB)
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/teacher/modules/" + moduleB)
                        .header(U, "MODTEACHERA").header(T, "mtok-a"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/teacher/modules/" + moduleB))
                .andExpect(status().isUnauthorized());
    }
}

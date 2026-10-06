package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.CourseLessonRepository;
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
 * PART 9B: Teacher lesson management + authorisation tests.
 *
 * <p>Proves the full chain authenticated teacher -&gt; assigned course -&gt; module -&gt; lesson: teacher A
 * can add / edit / describe / reorder / activate / deactivate lessons inside their own course's
 * module, and every one of those operations is rejected (403) when aimed at teacher B's module or
 * lesson - including via hand-edited ids.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherLessonManagementTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";

    private Long moduleA;
    private Long moduleB;
    private Long lessonB;

    @BeforeEach
    void seed() {
        User a = teacher("les-a@test.io", "LESTEACHERA", "ltok-a");
        User b = teacher("les-b@test.io", "LESTEACHERB", "ltok-b");
        Course ca = course("LESA", "Lesson Test Java", a.getId());
        Course cb = course("LESB", "Lesson Test Python", b.getId());
        moduleA = modules.save(CourseModule.builder()
                .course(ca).title("Java Fundamentals").displayOrder(1).active(true).build()).getId();
        CourseModule mb = modules.save(CourseModule.builder()
                .course(cb).title("Python Basics").displayOrder(1).active(true).build());
        moduleB = mb.getId();
        lessonB = lessons.save(CourseLesson.builder()
                .module(mb).title("B lesson").displayOrder(1).active(true).build()).getId();
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

    private Long addLesson(String title) throws Exception {
        String body = mvc.perform(post("/api/v1/teacher/modules/" + moduleA + "/lessons")
                        .header(U, "LESTEACHERA").header(T, "ltok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moduleId").value(moduleA))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void teacherCanManageLessonsOfAssignedCourse() throws Exception {
        Long intro = addLesson("Introduction");
        Long variables = addLesson("Variables");
        addLesson("Data Types");

        // lessons keep their module and get sequential display orders
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESA").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCount").value(3))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("Introduction"))
                .andExpect(jsonPath("$.data.modules[0].lessons[1].displayOrder").value(2));

        // edit title + add a description, then edit the description again
        mvc.perform(put("/api/v1/teacher/lessons/" + intro)
                        .header(U, "LESTEACHERA").header(T, "ltok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Intro\",\"description\":\"Course overview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Intro"))
                .andExpect(jsonPath("$.data.description").value("Course overview"));

        mvc.perform(put("/api/v1/teacher/lessons/" + intro)
                        .header(U, "LESTEACHERA").header(T, "ltok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Intro\",\"description\":\"Updated overview\",\"displayOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Updated overview"));

        // reorder: Variables moves above Introduction
        mvc.perform(patch("/api/v1/teacher/lessons/" + variables + "/move?direction=-1")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(1));
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESA").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("Variables"))
                .andExpect(jsonPath("$.data.modules[0].lessons[1].title").value("Intro"));

        // deactivate + activate through the existing `active` flag
        mvc.perform(patch("/api/v1/teacher/lessons/" + intro + "/status?active=false")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(patch("/api/v1/teacher/lessons/" + intro + "/status?active=true")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.description").value("Updated overview"));
    }

    @Test
    void teacherCannotTouchAnotherTeachersLessons() throws Exception {
        // create inside an unassigned course's module
        mvc.perform(post("/api/v1/teacher/modules/" + moduleB + "/lessons")
                        .header(U, "LESTEACHERA").header(T, "ltok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\"}"))
                .andExpect(status().isForbidden());

        // edit another teacher's lesson via a manipulated id
        mvc.perform(put("/api/v1/teacher/lessons/" + lessonB)
                        .header(U, "LESTEACHERA").header(T, "ltok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());

        // reorder + status of another teacher's lesson
        mvc.perform(patch("/api/v1/teacher/lessons/" + lessonB + "/move?direction=1")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/teacher/lessons/" + lessonB + "/status?active=false")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isForbidden());

        // unauthenticated
        mvc.perform(put("/api/v1/teacher/lessons/" + lessonB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isUnauthorized());

        // and the row is untouched
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESB").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERB").header(T, "ltok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("B lesson"));
    }

    @Test
    void teacherCanDeleteLessonAndRemainingLessonsAreResequenced() throws Exception {
        Long intro = addLesson("1. Introduction");
        Long basics = addLesson("2. Basics");
        Long analysis = addLesson("3. Technical Analysis");
        Long advanced = addLesson("4. Advanced Concepts");
        Long conclusion = addLesson("5. Conclusion");

        // Verify initial 5 lessons with sequential displayOrder 1..5
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESA").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCount").value(5));

        // Delete lesson 3 (Technical Analysis)
        mvc.perform(delete("/api/v1/teacher/lessons/" + analysis)
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lesson deleted"));

        // Verify remaining 4 lessons are automatically renumbered 1..4 with NO gaps
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESA").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCount").value(4))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("1. Introduction"))
                .andExpect(jsonPath("$.data.modules[0].lessons[0].displayOrder").value(1))
                .andExpect(jsonPath("$.data.modules[0].lessons[1].title").value("2. Basics"))
                .andExpect(jsonPath("$.data.modules[0].lessons[1].displayOrder").value(2))
                .andExpect(jsonPath("$.data.modules[0].lessons[2].title").value("4. Advanced Concepts"))
                .andExpect(jsonPath("$.data.modules[0].lessons[2].displayOrder").value(3))
                .andExpect(jsonPath("$.data.modules[0].lessons[3].title").value("5. Conclusion"))
                .andExpect(jsonPath("$.data.modules[0].lessons[3].displayOrder").value(4));

        // Delete remaining lessons until module is empty
        mvc.perform(delete("/api/v1/teacher/lessons/" + intro).header(U, "LESTEACHERA").header(T, "ltok-a")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/teacher/lessons/" + basics).header(U, "LESTEACHERA").header(T, "ltok-a")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/teacher/lessons/" + advanced).header(U, "LESTEACHERA").header(T, "ltok-a")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/teacher/lessons/" + conclusion).header(U, "LESTEACHERA").header(T, "ltok-a")).andExpect(status().isOk());

        // Empty module remains intact
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESA").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessonCount").value(0))
                .andExpect(jsonPath("$.data.modules.length()").value(1))
                .andExpect(jsonPath("$.data.modules[0].lessons.length()").value(0));
    }

    @Test
    void teacherCannotDeleteAnotherTeachersLesson() throws Exception {
        // Teacher A cannot delete Teacher B's lesson
        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonB)
                        .header(U, "LESTEACHERA").header(T, "ltok-a"))
                .andExpect(status().isForbidden());

        // Unauthenticated caller cannot delete lesson
        mvc.perform(delete("/api/v1/teacher/lessons/" + lessonB))
                .andExpect(status().isUnauthorized());

        // Teacher B's lesson remains intact
        mvc.perform(get("/api/v1/teacher/courses/" + courses.findByCode("LESB").orElseThrow().getId() + "/content")
                        .header(U, "LESTEACHERB").header(T, "ltok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("B lesson"));
    }
}

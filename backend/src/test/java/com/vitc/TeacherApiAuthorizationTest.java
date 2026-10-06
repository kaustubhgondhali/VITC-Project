package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 10A - strict backend authorization for every Teacher API.
 *
 * <p>Scenario: Teacher A is assigned "Java", Teacher B is assigned "Python". Teacher A must be
 * able to manage Java, and every single Teacher API must answer 403 Forbidden when Teacher A
 * tampers with ids belonging to Python - courses, modules, lessons, content, reorder,
 * activate/deactivate and video endpoints alike.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherApiAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;

    private static final String U = "X-Teacher-Username";
    private static final String T = "X-Teacher-Token";
    private static final String A_USER = "AUTHZTEACHERA";
    private static final String A_TOKEN = "authz-tok-a";

    private Long javaCourse;
    private Long pythonCourse;
    private Long javaModule;
    private Long pythonModule;
    private Long javaLesson;
    private Long pythonLesson;

    @BeforeEach
    void seed() {
        User a = teacher("authz-a@test.io", A_USER, A_TOKEN);
        User b = teacher("authz-b@test.io", "AUTHZTEACHERB", "authz-tok-b");

        Course java = course("AUTHZJAVA", "Authz Java", a.getId());
        Course python = course("AUTHZPY", "Authz Python", b.getId());
        javaCourse = java.getId();
        pythonCourse = python.getId();

        CourseModule mj = modules.save(CourseModule.builder()
                .course(java).title("Java Module").displayOrder(1).active(true).build());
        CourseModule mp = modules.save(CourseModule.builder()
                .course(python).title("Python Module").displayOrder(1).active(true).build());
        javaModule = mj.getId();
        pythonModule = mp.getId();

        javaLesson = lessons.save(CourseLesson.builder()
                .module(mj).title("Java Lesson").displayOrder(1).active(true).build()).getId();
        pythonLesson = lessons.save(CourseLesson.builder()
                .module(mp).title("Python Lesson").displayOrder(1).active(true).build()).getId();
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

    /* ---------------- assigned course: everything works ---------------- */

    @Test
    void assignedTeacherCanUseEveryTeacherApiOnOwnCourse() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses").header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(javaCourse));
        mvc.perform(get("/api/v1/teacher/courses/" + javaCourse).header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/teacher/courses/" + javaCourse + "/content")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/teacher/courses/" + javaCourse + "/modules")
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second Module\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/teacher/modules/" + javaModule)
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Java Module Renamed\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/teacher/modules/" + javaModule + "/move?direction=1")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/teacher/modules/" + javaModule + "/status?active=false")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk());
        mvc.perform(videoUpload(javaLesson)
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isOk());
    }

    /* ---------------- unassigned course: 403 on every API ---------------- */

    @Test
    void courseApisRejectUnassignedCourse() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses/" + pythonCourse).header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teacher/courses/" + pythonCourse + "/content")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void moduleApisRejectForeignModules() throws Exception {
        mvc.perform(post("/api/v1/teacher/courses/" + pythonCourse + "/modules")
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Injected\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/teacher/modules/" + pythonModule)
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/teacher/modules/" + pythonModule + "/move?direction=1")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/teacher/modules/" + pythonModule + "/status?active=false")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void lessonAndVideoApisRejectForeignLessons() throws Exception {
        mvc.perform(post("/api/v1/teacher/modules/" + pythonModule + "/lessons")
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Injected lesson\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/teacher/lessons/" + pythonLesson)
                        .header(U, A_USER).header(T, A_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hijacked lesson\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/teacher/lessons/" + pythonLesson + "/move?direction=1")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/teacher/lessons/" + pythonLesson + "/status?active=false")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teacher/lessons/" + pythonLesson + "/video")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(videoUpload(pythonLesson)
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/teacher/lessons/" + pythonLesson + "/video")
                        .header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
    }

    /* ---------------- role checks ---------------- */

    /** A perfectly valid non-teacher (staff) session is authenticated but NOT a teacher -> 403. */
    @Test
    void nonTeacherRoleWithValidSessionIsForbidden() throws Exception {
        User admin = users.findByUsernameIgnoreCase("AUTHZADMIN").orElseGet(() -> users.save(User.builder()
                .fullName("Authz Staff").email("authz-admin@test.io").username("AUTHZADMIN")
                .passwordHash("x").role(UserRole.STAFF).status(UserStatus.ACTIVE).build()));
        admin.setSessionToken("authz-admin-tok");
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(admin);

        mvc.perform(get("/api/v1/teacher/courses").header(U, "AUTHZADMIN").header(T, "authz-admin-tok"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teacher/dashboard/stats")
                        .header(U, "AUTHZADMIN").header(T, "authz-admin-tok"))
                .andExpect(status().isForbidden());
    }

    /** A suspended teacher keeps a token but loses access. */
    @Test
    void inactiveTeacherIsForbidden() throws Exception {
        User a = users.findByUsernameIgnoreCase(A_USER).orElseThrow();
        a.setStatus(UserStatus.INACTIVE);
        users.save(a);

        mvc.perform(get("/api/v1/teacher/courses/" + javaCourse).header(U, A_USER).header(T, A_TOKEN))
                .andExpect(status().isForbidden());
    }

    /** No session at all -> 401, never processed. */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/teacher/courses/" + javaCourse)).andExpect(status().isUnauthorized());
        mvc.perform(videoUpload(javaLesson))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder videoUpload(
            Long lessonId) {
        // Real ISO-BMFF header: uploads are now validated against their actual container bytes.
        byte[] payload = new byte[64];
        System.arraycopy(new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'},
                0, payload, 0, 12);
        MockMultipartFile file = new MockMultipartFile(
                "file", "authorization-test.mp4", "video/mp4", payload);
        return multipart(HttpMethod.PUT, "/api/v1/teacher/lessons/" + lessonId + "/video").file(file);
    }
}

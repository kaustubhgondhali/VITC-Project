package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 10C - Main Admin -&gt; Teachers management, end to end:
 * create teacher -&gt; assign course -&gt; teacher accesses assigned course -&gt;
 * unassigned course 403 -&gt; admin APIs 403 -&gt; deactivate -&gt; access denied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminTeacherManagementTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdminRepository admins;
    @Autowired CourseRepository courses;
    @Autowired PasswordEncoder encoder;

    private static final String ADMIN = "tmadmin";
    private static final String ADMIN_TOKEN = "tm-admin-token";

    private Long assignedCourseId;
    private Long otherCourseId;

    @BeforeEach
    void seed() {
        Admin a = admins.findByUsernameIgnoreCase(ADMIN).orElseGet(() -> admins.save(Admin.builder()
                .username(ADMIN).email("tmadmin@test.io").fullName("TM Admin")
                .passwordHash("x").build()));
        a.setActive(true);
        a.setSessionToken(ADMIN_TOKEN);
        a.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(a);

        assignedCourseId = courses.findByCode("TMJAVA").orElseGet(() -> courses.save(Course.builder()
                .code("TMJAVA").title("TM Java").price(BigDecimal.TEN).active(true).build())).getId();
        otherCourseId = courses.findByCode("TMPY").orElseGet(() -> courses.save(Course.builder()
                .code("TMPY").title("TM Python").price(BigDecimal.TEN).active(true).build())).getId();
        Course c1 = courses.findById(assignedCourseId).orElseThrow();
        c1.setTeacherId(null);
        courses.save(c1);
        Course c2 = courses.findById(otherCourseId).orElseThrow();
        c2.setTeacherId(null);
        courses.save(c2);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private User teacher(String username, UserStatus status) {
        User t = users.findByUsernameIgnoreCase(username).orElseGet(() -> users.save(User.builder()
                .fullName("Flow Teacher").email(username.toLowerCase() + "@test.io").username(username)
                .passwordHash(encoder.encode("Teach@1234")).role(UserRole.TEACHER).status(status).build()));
        t.setStatus(status);
        t.setSessionToken("tok-" + username);
        t.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        return users.save(t);
    }

    /* 1. Main Admin can create a teacher; only the hash is stored. */
    @Test
    void mainAdminCreatesTeacherWithHashedPassword() throws Exception {
        String body = """
                {"fullName":"New Teacher","username":"TMNEW1","email":"tmnew1@test.io"}""";
        mvc.perform(asAdmin(post("/api/v1/admin/teachers"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("TMNEW1"))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());

        User created = users.findByUsernameIgnoreCase("TMNEW1").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(created.getPasswordHash().startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.TEACHER, created.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(UserStatus.ACTIVE, created.getStatus());
    }

    /* 2. Teachers list never leaks a password hash or session token. */
    @Test
    void teacherListCarriesNoCredentials() throws Exception {
        teacher("TMLIST", UserStatus.ACTIVE);
        String json = mvc.perform(asAdmin(get("/api/v1/admin/teachers")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("passwordHash"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("sessionToken"));
    }

    /* 3. A Teacher session can never reach the Teachers management APIs. */
    @Test
    void teacherCannotManageTeachers() throws Exception {
        User t = teacher("TMDENY", UserStatus.ACTIVE);
        mvc.perform(get("/api/v1/admin/teachers")
                        .header("X-Teacher-Username", t.getUsername())
                        .header("X-Teacher-Token", t.getSessionToken()))
                .andExpect(status().isForbidden());
    }

    /* 4. Assignment drives real authorisation: assigned course OK, other course 403. */
    @Test
    void assignmentGrantsAccessToAssignedCourseOnly() throws Exception {
        User t = teacher("TMFLOW", UserStatus.ACTIVE);

        mvc.perform(asAdmin(put("/api/v1/admin/teachers/" + t.getId() + "/courses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\":[" + assignedCourseId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedCourseCount").value(1));

        mvc.perform(get("/api/v1/teacher/courses/" + assignedCourseId)
                        .header("X-Teacher-Username", t.getUsername())
                        .header("X-Teacher-Token", t.getSessionToken()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/teacher/courses/" + otherCourseId)
                        .header("X-Teacher-Username", t.getUsername())
                        .header("X-Teacher-Token", t.getSessionToken()))
                .andExpect(status().isForbidden());
    }

    /* 5. Removing an assignment withdraws access but preserves the course. */
    @Test
    void removingAssignmentPreservesCourseContent() throws Exception {
        User t = teacher("TMREMOVE", UserStatus.ACTIVE);
        mvc.perform(asAdmin(put("/api/v1/admin/teachers/" + t.getId() + "/courses"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\":[" + assignedCourseId + "]}"))
                .andExpect(status().isOk());

        mvc.perform(asAdmin(delete("/api/v1/admin/teachers/" + t.getId() + "/courses/" + assignedCourseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedCourseCount").value(0));

        // Course row still exists - only the authorisation was withdrawn.
        org.junit.jupiter.api.Assertions.assertTrue(courses.findById(assignedCourseId).isPresent());
        org.junit.jupiter.api.Assertions.assertNull(courses.findById(assignedCourseId).orElseThrow().getTeacherId());

        mvc.perform(get("/api/v1/teacher/courses/" + assignedCourseId)
                        .header("X-Teacher-Username", t.getUsername())
                        .header("X-Teacher-Token", t.getSessionToken()))
                .andExpect(status().isForbidden());
    }

    /* 6. Deactivation denies login AND every teacher API, backend enforced. */
    @Test
    void deactivatedTeacherIsDeniedEverywhere() throws Exception {
        User t = teacher("TMOFF", UserStatus.ACTIVE);
        mvc.perform(asAdmin(patch("/api/v1/admin/teachers/" + t.getId() + "/status?status=INACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // Session was killed -> stale token is rejected.
        mvc.perform(get("/api/v1/teacher/dashboard/stats")
                        .header("X-Teacher-Username", t.getUsername())
                        .header("X-Teacher-Token", "tok-TMOFF"))
                .andExpect(status().is4xxClientError());

        // Login is refused for an inactive account.
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"TMOFF\",\"password\":\"Teach@1234\"}"))
                .andExpect(status().is4xxClientError());

        // Reactivation restores login.
        mvc.perform(asAdmin(patch("/api/v1/admin/teachers/" + t.getId() + "/status?status=ACTIVE")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"TMOFF\",\"password\":\"Teach@1234\"}"))
                .andExpect(status().isOk());
    }

    /* 7. Password reset stores a new hash, kills the session and forces a change. */
    @Test
    void passwordResetIsHashedAndForcesChange() throws Exception {
        User t = teacher("TMRESET", UserStatus.ACTIVE);
        String oldHash = t.getPasswordHash();

        mvc.perform(asAdmin(post("/api/v1/admin/teachers/" + t.getId() + "/password-reset"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        User after = users.findById(t.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(oldHash, after.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(after.getPasswordHash().startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertNull(after.getSessionToken());
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(after.getMustChangePassword()));
    }

    /* 8. Editing a profile never touches credentials. */
    @Test
    void editTeacherKeepsCredentials() throws Exception {
        User t = teacher("TMEDIT", UserStatus.ACTIVE);
        String hash = t.getPasswordHash();
        mvc.perform(asAdmin(put("/api/v1/admin/teachers/" + t.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Edited Name\",\"email\":\"tmedit2@test.io\",\"phone\":\"9999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Edited Name"));
        org.junit.jupiter.api.Assertions.assertEquals(hash, users.findById(t.getId()).orElseThrow().getPasswordHash());
    }
}

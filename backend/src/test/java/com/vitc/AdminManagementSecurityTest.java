package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
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

/** Part 3: admin student management + course content management. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminManagementSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;

    private Long courseId;
    private Long moduleId;
    private Long studentId;

    private static final String U = "p3admin";
    private static final String T = "p3-admin-token";

    @BeforeEach
    void seed() {
        Admin a = admins.findByUsernameIgnoreCase(U).orElseGet(() -> admins.save(Admin.builder()
                .username(U).email("p3admin@test.io").fullName("P3 Admin").passwordHash("x").build()));
        a.setActive(true);
        a.setSessionToken(T);
        a.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        admins.save(a);

        Course c = courses.findAll().stream().filter(x -> "TP3".equals(x.getCode())).findFirst()
                .orElseGet(() -> courses.save(Course.builder().code("TP3").title("Part 3 Course")
                        .price(BigDecimal.TEN).active(true).build()));
        courseId = c.getId();
        moduleId = modules.save(CourseModule.builder().course(c).title("Seed Module")
                .displayOrder(1).active(true).build()).getId();

        User s = users.findByEmailIgnoreCase("p3stu@test.io").orElseGet(() -> users.save(User.builder()
                .fullName("P3 Student").email("p3stu@test.io").passwordHash("x").role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE).studentLoginId("VITCSTUP3").build()));
        s.setSessionToken("p3-stu-token");
        s.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        studentId = users.save(s).getId();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", U).header("X-Admin-Token", T);
    }

    /* 1. Every new admin endpoint rejects an unauthenticated caller. */
    @Test
    void adminEndpointsRequireAdminSession() throws Exception {
        mvc.perform(get("/api/v1/admin/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/students/" + studentId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/courses/" + courseId + "/content")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/courses/" + courseId + "/modules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/admin/modules/" + moduleId)).andExpect(status().isUnauthorized());
    }

    /* 2. A student session token is not an admin session (PART 10B: authenticated
       non-admin callers get 403 Forbidden instead of a login redirect). */
    @Test
    void studentTokenCannotReachAdminApi() throws Exception {
        mvc.perform(get("/api/v1/admin/students")
                        .header("X-Student-Id", "VITCSTUP3").header("X-Student-Token", "p3-stu-token"))
                .andExpect(status().is4xxClientError());
    }

    /* 3. Admin can list students with their purchased courses. */
    @Test
    void adminListsStudents() throws Exception {
        mvc.perform(auth(get("/api/v1/admin/students")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.studentLoginId=='VITCSTUP3')]").exists());
    }

    /* 4. Manual enrolment grants access and is flagged as manually granted. */
    @Test
    void manualEnrolmentIsGrantedAndFlagged() throws Exception {
        mvc.perform(auth(post("/api/v1/admin/students/" + studentId + "/enrollments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"reason\":\"comp access\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/v1/admin/students/" + studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data.courses[0].manuallyGranted").value(true));

        // the student can now open the course through the Part 2 student API
        mvc.perform(get("/api/v1/student/courses/" + courseId)
                        .header("X-Student-Id", "VITCSTUP3").header("X-Student-Token", "p3-stu-token"))
                .andExpect(status().isOk());
    }

    /* 5. Blocking a student revokes the portal. */
    @Test
    void blockedStudentLosesPortalAccess() throws Exception {
        mvc.perform(auth(patch("/api/v1/admin/students/" + studentId + "/status?status=BLOCKED")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/courses")
                        .header("X-Student-Id", "VITCSTUP3").header("X-Student-Token", "p3-stu-token"))
                .andExpect(status().isUnauthorized());
    }

    /* 6. Module + lesson CRUD, reorder and delete work end to end. */
    @Test
    void moduleAndLessonLifecycle() throws Exception {
        mvc.perform(auth(post("/api/v1/admin/courses/" + courseId + "/modules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second Module\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayOrder").value(2));

        mvc.perform(auth(post("/api/v1/admin/modules/" + moduleId + "/lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"L1\",\"videoUrl\":\"https://x/v\",\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(auth(post("/api/v1/admin/modules/" + moduleId + "/lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"L2\",\"videoUrl\":\"https://x/v2\",\"active\":true}"))
                .andExpect(status().isOk());

        CourseLesson l2 = lessons.findByModuleIdOrderByDisplayOrderAscIdAsc(moduleId).get(1);
        mvc.perform(auth(patch("/api/v1/admin/lessons/" + l2.getId() + "/move?direction=-1")))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/admin/courses/" + courseId + "/content")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modules[0].lessons[0].title").value("L2"));

        mvc.perform(auth(delete("/api/v1/admin/lessons/" + l2.getId()))).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/admin/modules/" + moduleId))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/admin/courses/" + courseId + "/content")))
                .andExpect(jsonPath("$.data.modules[?(@.title=='Seed Module')]").doesNotExist());
    }
}

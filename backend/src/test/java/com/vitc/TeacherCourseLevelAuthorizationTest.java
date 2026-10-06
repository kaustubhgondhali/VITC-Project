package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 11B-3 - Teacher course-level authorization.
 *
 * <p>Two teachers, each with their own course, module, lesson and enrolled student. Every check
 * below hits the real HTTP endpoint with hand-crafted ids to prove the backend - not the
 * frontend - is what decides access.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherCourseLevelAuthorizationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired CourseLessonRepository lessons;
    @Autowired EnrollmentRepository enrollments;

    private static final String TU = "X-Teacher-Username";
    private static final String TT = "X-Teacher-Token";

    private Long javaCourseId;
    private Long pythonCourseId;
    private Long javaModuleId;
    private Long pythonModuleId;
    private Long javaLessonId;
    private Long pythonLessonId;
    private Long studentId;

    @BeforeEach
    void seed() {
        User teacherA = users.findByUsernameIgnoreCase("P3TEACHERA").orElseGet(() -> users.save(User.builder()
                .fullName("P3 Teacher A").email("p3-teachera@test.io").username("P3TEACHERA")
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacherA.setSessionToken("p3-tok-a");
        teacherA.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacherA);

        User teacherB = users.findByUsernameIgnoreCase("P3TEACHERB").orElseGet(() -> users.save(User.builder()
                .fullName("P3 Teacher B").email("p3-teacherb@test.io").username("P3TEACHERB")
                .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacherB.setSessionToken("p3-tok-b");
        teacherB.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(teacherB);

        Course java = courses.findByCode("P3JAVA").orElseGet(() -> courses.save(Course.builder()
                .code("P3JAVA").title("P3 Java").price(BigDecimal.TEN).active(true)
                .teacherId(teacherA.getId()).build()));
        javaCourseId = java.getId();

        Course python = courses.findByCode("P3PY").orElseGet(() -> courses.save(Course.builder()
                .code("P3PY").title("P3 Python").price(BigDecimal.TEN).active(true)
                .teacherId(teacherB.getId()).build()));
        pythonCourseId = python.getId();

        javaModuleId = modules.save(CourseModule.builder()
                .course(java).title("Java Module").displayOrder(1).active(true).build()).getId();
        pythonModuleId = modules.save(CourseModule.builder()
                .course(python).title("Python Module").displayOrder(1).active(true).build()).getId();

        CourseModule javaModuleRef = modules.findById(javaModuleId).orElseThrow();
        CourseModule pythonModuleRef = modules.findById(pythonModuleId).orElseThrow();

        javaLessonId = lessons.save(CourseLesson.builder()
                .module(javaModuleRef).title("Java Lesson").videoUrl("https://video/java.mp4")
                .duration("10:00").displayOrder(1).active(true).build()).getId();
        pythonLessonId = lessons.save(CourseLesson.builder()
                .module(pythonModuleRef).title("Python Lesson").videoUrl("https://video/py.mp4")
                .duration("10:00").displayOrder(1).active(true).build()).getId();

        User student = users.findByStudentLoginIdIgnoreCase("VITCSTU90003").orElseGet(() -> users.save(User.builder()
                .fullName("P3 Student").email("p3-student@test.io").studentLoginId("VITCSTU90003")
                .passwordHash("x").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build()));
        studentId = student.getId();

        enrollments.save(Enrollment.builder()
                .studentName(student.getFullName()).email(student.getEmail()).phone("9999999999")
                .course(java).user(student).amount(BigDecimal.TEN).status(EnrollmentStatus.ACTIVE).build());
    }

    private MockHttpServletRequestBuilder asTeacherA(MockHttpServletRequestBuilder b) {
        return b.header(TU, "P3TEACHERA").header(TT, "p3-tok-a");
    }

    /* Teacher + Assigned Course -> ALLOWED */
    @Test
    void teacherAssignedCourseAllowed() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + javaCourseId))).andExpect(status().isOk());
    }

    /* Teacher + Unassigned/Another Teacher's Course -> 403 */
    @Test
    void teacherUnassignedCourseForbidden() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + pythonCourseId))).andExpect(status().isForbidden());
    }

    /* Teacher + Assigned Module -> ALLOWED */
    @Test
    void teacherAssignedModuleAllowed() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + javaCourseId + "/content")))
                .andExpect(status().isOk());
    }

    /* Teacher + Module from Unassigned Course -> 403 (both reading the tree and acting on the module id) */
    @Test
    void teacherModuleFromUnassignedCourseForbidden() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + pythonCourseId + "/content")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacherA(patch("/api/v1/teacher/modules/" + pythonModuleId + "/status")
                        .param("active", "false")))
                .andExpect(status().isForbidden());
    }

    /* Teacher + Assigned Lesson / Video -> ALLOWED */
    @Test
    void teacherAssignedLessonAndVideoAllowed() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/lessons/" + javaLessonId + "/video")))
                .andExpect(status().isOk());
    }

    /* Teacher + Lesson/Video from Unassigned Course -> 403 */
    @Test
    void teacherLessonAndVideoFromUnassignedCourseForbidden() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/lessons/" + pythonLessonId + "/video")))
                .andExpect(status().isForbidden());
    }

    /* Teacher + Assigned Course Student Progress -> ALLOWED */
    @Test
    void teacherAssignedCourseStudentProgressAllowed() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + javaCourseId + "/students")))
                .andExpect(status().isOk());
        mvc.perform(asTeacherA(get(
                "/api/v1/teacher/courses/" + javaCourseId + "/students/" + studentId + "/progress")))
                .andExpect(status().isOk());
    }

    /* Teacher + Unauthorized Course Student Progress -> 403 (even with a real student id) */
    @Test
    void teacherUnauthorizedCourseStudentProgressForbidden() throws Exception {
        mvc.perform(asTeacherA(get("/api/v1/teacher/courses/" + pythonCourseId + "/students")))
                .andExpect(status().isForbidden());
        mvc.perform(asTeacherA(get(
                "/api/v1/teacher/courses/" + pythonCourseId + "/students/" + studentId + "/progress")))
                .andExpect(status().isForbidden());
    }
}

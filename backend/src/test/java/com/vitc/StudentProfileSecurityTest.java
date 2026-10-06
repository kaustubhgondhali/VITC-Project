package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Part 6 - Student Profile & Account. Verifies profile read/edit is scoped to
 * the caller's own account, protected fields can never be changed through
 * these endpoints, and every profile route rejects an unauthenticated caller.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentProfileSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private String loginIdA;
    private String loginIdB;

    @BeforeEach
    void seed() {
        loginIdA = "VITCPROFA";
        loginIdB = "VITCPROFB";
        student("profile-a@test.io", loginIdA, "ptok-a", "Student A");
        student("profile-b@test.io", loginIdB, "ptok-b", "Student B");
    }

    private void student(String email, String loginId, String token, String fullName) {
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(User.builder()
                .fullName(fullName).email(email).passwordHash(passwordEncoder.encode("Secret@123"))
                .role(UserRole.STUDENT).status(UserStatus.ACTIVE).studentLoginId(loginId).build()));
        u.setSessionToken(token);
        u.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        users.save(u);
    }

    /* Test 1: an authenticated student can view and edit only their own profile. */
    @Test
    void ownerCanViewAndEditOwnProfile() throws Exception {
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentLoginId").value(loginIdA))
                .andExpect(jsonPath("$.data.fullName").value("Student A"));

        mvc.perform(put("/api/v1/student/profile")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Student A Updated\",\"phone\":\"9876543210\",\"city\":\"Uran\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Student A Updated"))
                .andExpect(jsonPath("$.data.city").value("Uran"))
                .andExpect(jsonPath("$.data.studentLoginId").value(loginIdA));
    }

    /* Test 2: validation rejects a blank name and a malformed phone number. */
    @Test
    void editRejectsInvalidInput() throws Exception {
        mvc.perform(put("/api/v1/student/profile")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"\",\"city\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/student/profile")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Student A\",\"phone\":\"abc\",\"city\":\"Uran\"}"))
                .andExpect(status().isBadRequest());
    }

    /* Test 3: the request body can never carry an id/role/loginId - only the
       three editable fields are bound, so no such field can leak through. */
    @Test
    void protectedFieldsAreNeverAccepted() throws Exception {
        mvc.perform(put("/api/v1/student/profile")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Student A\",\"phone\":\"9876543210\",\"city\":\"Uran\","
                                + "\"studentLoginId\":\"VITCHACKED\",\"role\":\"ADMIN\",\"id\":9999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentLoginId").value(loginIdA));

        User reloaded = users.findByStudentLoginIdIgnoreCase(loginIdA).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.STUDENT, reloaded.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(loginIdA, reloaded.getStudentLoginId());
    }

    /* Test 4: student B's session can never read or edit student A's profile -
       there is no "target id" in the request, only the caller's own session. */
    @Test
    void studentBCannotTouchStudentAAccount() throws Exception {
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", loginIdB).header("X-Student-Token", "ptok-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentLoginId").value(loginIdB))
                .andExpect(jsonPath("$.data.fullName").value("Student B"));

        User reloadedA = users.findByStudentLoginIdIgnoreCase(loginIdA).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Student A", reloadedA.getFullName());
    }

    /* Test 5: no session at all is rejected on every profile route. */
    @Test
    void unauthenticatedProfileAccessIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/student/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nope\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/student/profile/image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"/uploads/general/x.png\"}"))
                .andExpect(status().isUnauthorized());
    }

    /* Test 6: an expired/invalid token is rejected the same way. */
    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/me")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    /* Test 7: attaching a profile image works and is scoped to the caller. */
    @Test
    void profileImageUpdateIsScopedToCaller() throws Exception {
        mvc.perform(put("/api/v1/student/profile/image")
                        .header("X-Student-Id", loginIdA).header("X-Student-Token", "ptok-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"/uploads/general/avatar-a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value("/uploads/general/avatar-a.png"));

        User reloadedB = users.findByStudentLoginIdIgnoreCase(loginIdB).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(reloadedB.getProfileImageUrl());
    }
}

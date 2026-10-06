package com.vitc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.PasswordResetOtp;
import com.vitc.entity.User;
import com.vitc.entity.enums.RecoveryPortal;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.PasswordResetOtpRepository;
import com.vitc.repository.UserRepository;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherForgotPasswordTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetOtpRepository otps;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private static final String TEACHER_USERNAME = "VITCteacher";
    private static final String TEACHER_EMAIL = "teacher@vitc.in";
    private static final String DEFAULT_PASSWORD = "VITC@123";

    @BeforeEach
    void ensureTeacher() {
        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> users.save(
                User.builder()
                        .username(TEACHER_USERNAME)
                        .fullName("VITC Demo Teacher")
                        .email(TEACHER_EMAIL)
                        .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                        .role(UserRole.TEACHER)
                        .status(UserStatus.ACTIVE)
                        .build()));
        teacher.setPasswordHash(encoder.encode(DEFAULT_PASSWORD));
        teacher.setStatus(UserStatus.ACTIVE);
        users.save(teacher);
    }

    @Test
    void teacherForgotPasswordIssuesRecoveryToken() throws Exception {
        mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_USERNAME + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recoveryToken").isNotEmpty())
                .andExpect(jsonPath("$.data.targetMasked").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInMinutes").value(5));

        User teacher = users.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseThrow();
        List<PasswordResetOtp> activeOtps = otps.findByAccountIdAndAccountRoleAndUsedFalse(teacher.getId(), RecoveryPortal.TEACHER);
        assertFalse(activeOtps.isEmpty());
        assertNotNull(activeOtps.get(0).getOtpHash());
        assertNotNull(activeOtps.get(0).getExpiresAt());
    }

    @Test
    void teacherResetPasswordSucceedsAndAllowsLoginWithNewPassword() throws Exception {
        String res = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = mapper.readTree(res).path("data").path("recoveryToken").asText();

        // Preset known OTP on the generated record for verification
        PasswordResetOtp otpRecord = otps.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.TEACHER).orElseThrow();
        String plainOtp = "123456";
        otpRecord.setOtpHash(encoder.encode(plainOtp));
        otps.save(otpRecord);

        // Verify OTP
        String verifyRes = mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resetToken = mapper.readTree(verifyRes).path("data").path("resetToken").asText();

        String newPassword = "TeacherNewSecret@123";
        String resetPayload = "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}";

        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        PasswordResetOtp consumedOtp = otps.findByResetTokenAndAccountRole(resetToken, RecoveryPortal.TEACHER).orElseThrow();
        assertTrue(consumedOtp.getUsed());

        // Old password must fail
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + DEFAULT_PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError());

        // New password must succeed
        mvc.perform(post("/api/v1/teacher/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEACHER_USERNAME + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());
    }

    @Test
    void teacherResetPasswordRejectsMismatchAndInvalidTokenAndReplay() throws Exception {
        String res = mvc.perform(post("/api/v1/teacher/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"" + TEACHER_USERNAME + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String recoveryToken = mapper.readTree(res).path("data").path("recoveryToken").asText();
        PasswordResetOtp otpRecord = otps.findByRecoveryTokenAndAccountRole(recoveryToken, RecoveryPortal.TEACHER).orElseThrow();
        String plainOtp = "654321";
        otpRecord.setOtpHash(encoder.encode(plainOtp));
        otps.save(otpRecord);

        // Verify OTP
        String verifyRes = mvc.perform(post("/api/v1/teacher/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryToken\":\"" + recoveryToken + "\",\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String resetToken = mapper.readTree(verifyRes).path("data").path("resetToken").asText();

        // Password mismatch
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"NewPass@123\",\"confirmPassword\":\"Different@123\"}"))
                .andExpect(status().isBadRequest());

        // Password too short
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        // Invalid reset code
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"invalid-token-123\",\"newPassword\":\"NewPass@123\",\"confirmPassword\":\"NewPass@123\"}"))
                .andExpect(status().isBadRequest());

        // Successful reset consumes the token
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"ValidNewPass@123\",\"confirmPassword\":\"ValidNewPass@123\"}"))
                .andExpect(status().isOk());

        // Replay of the same token must fail
        mvc.perform(post("/api/v1/teacher/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"AnotherPass@123\",\"confirmPassword\":\"AnotherPass@123\"}"))
                .andExpect(status().isBadRequest());
    }
}


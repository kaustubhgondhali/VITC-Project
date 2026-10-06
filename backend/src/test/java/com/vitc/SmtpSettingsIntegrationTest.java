package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of the admin-only SMTP settings: auth guard, encrypted
 * storage, password masking/omission from every response, and preserving the
 * saved password when an update omits it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmtpSettingsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;
    @Autowired SmtpSettingRepository settingsRepository;
    @Autowired CryptoService cryptoService;
    @Autowired ObjectMapper mapper;

    private static final String USER = "smtp-settings-admin";
    private static final String TOKEN = "test-smtp-session-token";

    @BeforeEach
    void seedAdmin() {
        adminRepository.findByUsernameIgnoreCase(USER).ifPresent(adminRepository::delete);
        settingsRepository.findBySettingsKey("DEFAULT").ifPresent(settingsRepository::delete);
        Admin admin = new Admin();
        admin.setUsername(USER);
        admin.setEmail(USER + "@vitc.in");
        admin.setFullName("SMTP Settings Admin");
        admin.setPasswordHash("irrelevant");
        admin.setRole(AdminRole.SUPER_ADMIN);
        admin.setActive(true);
        admin.setSessionToken(TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);
    }

    @Test
    void settingsApiRejectsAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/admin/smtp-settings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/smtp-settings/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"owner@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void firstSaveRequiresAPasswordAndNeverReturnsIt() throws Exception {
        String noPassword = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("host", "smtp.gmail.com");
            put("port", 587);
            put("username", "vitc@gmail.com");
            put("password", "");
            put("fromEmail", "vitc@gmail.com");
            put("fromName", "VITC");
        }});
        mvc.perform(put("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noPassword))
                .andExpect(status().isBadRequest());

        String withPassword = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("host", "smtp.gmail.com");
            put("port", 587);
            put("username", "vitc@gmail.com");
            put("password", "super-secret-app-password");
            put("fromEmail", "vitc@gmail.com");
            put("fromName", "VITC");
        }});
        mvc.perform(put("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withPassword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.host").value("smtp.gmail.com"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.passwordConfigured").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.maskedPassword")
                        .value("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"));

        var stored = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(stored.getEncryptedPassword()).isNotEqualTo("super-secret-app-password");
        assertThat(cryptoService.decrypt(stored.getEncryptedPassword())).isEqualTo("super-secret-app-password");
    }

    @Test
    void updatingOtherFieldsWithoutAPasswordKeepsThePreviousOne() throws Exception {
        String initial = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("host", "smtp.gmail.com");
            put("port", 587);
            put("username", "vitc@gmail.com");
            put("password", "first-app-password");
            put("fromEmail", "vitc@gmail.com");
            put("fromName", "VITC");
        }});
        mvc.perform(put("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initial))
                .andExpect(status().isOk());

        String update = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("host", "smtp.gmail.com");
            put("port", 465);
            put("username", "vitc@gmail.com");
            put("password", "");
            put("fromEmail", "no-reply@vitc.in");
            put("fromName", "VITC Support");
        }});
        mvc.perform(put("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.port").value(465))
                .andExpect(jsonPath("$.data.fromEmail").value("no-reply@vitc.in"))
                .andExpect(jsonPath("$.data.passwordConfigured").value(true));

        var stored = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(cryptoService.decrypt(stored.getEncryptedPassword())).isEqualTo("first-app-password");
    }

    @Test
    void invalidPortIsRejected() throws Exception {
        String body = mapper.writeValueAsString(new LinkedHashMap<String, Object>() {{
            put("host", "smtp.gmail.com");
            put("port", 99999);
            put("username", "vitc@gmail.com");
            put("password", "app-password");
            put("fromEmail", "vitc@gmail.com");
            put("fromName", "VITC");
        }});
        mvc.perform(put("/api/v1/admin/smtp-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}

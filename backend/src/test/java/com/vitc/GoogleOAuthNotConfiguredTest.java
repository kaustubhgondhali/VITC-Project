package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import com.vitc.repository.AdminRepository;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Without GOOGLE_CLIENT_ID / SECRET the screen is told exactly what is missing, and SMTP is unaffected. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.google.client-id=", "app.google.client-secret="})
class GoogleOAuthNotConfiguredTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;

    private static final String USER = "google-off-admin";
    private static final String TOKEN = "test-google-off-token";

    @BeforeEach
    void seed() {
        adminRepository.findByUsernameIgnoreCase(USER).ifPresent(adminRepository::delete);
        Admin admin = new Admin();
        admin.setUsername(USER);
        admin.setEmail(USER + "@vitc.in");
        admin.setFullName("Google Off Admin");
        admin.setPasswordHash("irrelevant");
        admin.setRole(AdminRole.SUPER_ADMIN);
        admin.setActive(true);
        admin.setSessionToken(TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);
    }

    @Test
    void reportsMissingConfigurationClearly() throws Exception {
        mvc.perform(get("/api/v1/admin/smtp-settings/google")
                        .header("X-Admin-Username", USER).header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.missing[0]").value("GOOGLE_CLIENT_ID"))
                .andExpect(jsonPath("$.data.missing[1]").value("GOOGLE_CLIENT_SECRET"));
        mvc.perform(get("/api/v1/admin/smtp-settings/google/connect")
                        .header("X-Admin-Username", USER).header("X-Admin-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("GOOGLE_CLIENT_ID")));
    }
}

package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.PaymentGatewaySettingRepository;
import com.vitc.security.CryptoService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of the configurable payment gateway: admin-only access,
 * encrypted storage, secret masking and the public gateway status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSettingsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;
    @Autowired PaymentGatewaySettingRepository settingsRepository;
    @Autowired CryptoService cryptoService;
    @Autowired ObjectMapper mapper;

    private static final String USER = "settings-admin";
    private static final String TOKEN = "test-session-token";

    @BeforeEach
    void seedAdmin() {
        adminRepository.findByUsernameIgnoreCase(USER).ifPresent(adminRepository::delete);
        Admin admin = new Admin();
        admin.setUsername(USER);
        admin.setEmail(USER + "@vitc.in");
        admin.setFullName("Settings Admin");
        admin.setPasswordHash("irrelevant");
        admin.setRole(AdminRole.SUPER_ADMIN);
        admin.setActive(true);
        admin.setSessionToken(TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);
    }

    @Test
    void settingsApiRejectsAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/admin/payment-settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/payment-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSaveRazorpayCredentialsAndSecretIsEncryptedAndMasked() throws Exception {
        String body = mapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("gateway", "RAZORPAY");
            put("mode", "TEST");
            put("keyId", "rzp_test_ABC123456789");
            put("keySecret", "super-secret-value");
            put("webhookSecret", "hook-secret-value");
            put("enabled", false);
        }});

        mvc.perform(put("/api/v1/admin/payment-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keyId").value("rzp_test_ABC123456789"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.keySecret").doesNotExist())
                .andExpect(jsonPath("$.data.maskedKeySecret").value("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"));

        var stored = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(stored.getEncryptedKeySecret()).isNotEqualTo("super-secret-value");
        assertThat(cryptoService.decrypt(stored.getEncryptedKeySecret())).isEqualTo("super-secret-value");
        assertThat(cryptoService.decrypt(stored.getEncryptedWebhookSecret())).isEqualTo("hook-secret-value");
    }

    @Test
    void liveModeRejectsATestKeyId() throws Exception {
        String body = "{\"gateway\":\"RAZORPAY\",\"mode\":\"LIVE\",\"keyId\":\"rzp_test_ABC123456789\","
                + "\"keySecret\":\"s\",\"enabled\":false}";
        mvc.perform(put("/api/v1/admin/payment-settings")
                        .header("X-Admin-Username", USER)
                        .header("X-Admin-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicStatusIsCredentialFreeAndFallsBackToMock() throws Exception {
        mvc.perform(get("/api/v1/payment/gateway"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gateway").value("MOCK"))
                .andExpect(jsonPath("$.data.keyId").doesNotExist());
    }

    @Test
    void webhookWithoutAValidSignatureIsRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/payment/razorpay/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "deadbeef")
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().is4xxClientError());
    }
}

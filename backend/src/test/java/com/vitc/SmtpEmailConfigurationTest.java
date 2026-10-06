package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.AuditLog;
import com.vitc.entity.SmtpSetting;
import com.vitc.entity.enums.AdminRole;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.AuditLogRepository;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.service.EmailService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Email Configuration: provider registry and detection, Gmail App Password handling,
 * write-only encrypted credential (never returned), credential replacement, transport
 * validation, audit trail, the /email-config alias, and the Sender Name / Reply-To headers
 * on a real SMTP conversation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmtpEmailConfigurationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;
    @Autowired SmtpSettingRepository settingsRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CryptoService cryptoService;
    @Autowired EmailService emailService;
    @Autowired ObjectMapper mapper;

    private static final String USER = "email-config-admin";
    private static final String TOKEN = "test-email-config-token";
    private static final String APP_PASSWORD = "abcd efgh ijkl mnop";
    private static final String APP_PASSWORD_COMPACT = "abcdefghijklmnop";

    @BeforeEach
    void seed() {
        adminRepository.findByUsernameIgnoreCase(USER).ifPresent(adminRepository::delete);
        settingsRepository.findBySettingsKey("DEFAULT").ifPresent(settingsRepository::delete);
        Admin admin = new Admin();
        admin.setUsername(USER);
        admin.setEmail(USER + "@vitc.in");
        admin.setFullName("Email Config Admin");
        admin.setPasswordHash("irrelevant");
        admin.setRole(AdminRole.SUPER_ADMIN);
        admin.setActive(true);
        admin.setSessionToken(TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", USER).header("X-Admin-Token", TOKEN);
    }

    private Map<String, Object> gmailBody(String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "GMAIL");
        m.put("fromEmail", "vitc.academy@gmail.com");
        m.put("fromName", "Vandana IT Courses");
        m.put("replyToEmail", "support@vitc.in");
        m.put("sendingDomain", "vitc.in");
        m.put("dkimSelector", "google");
        m.put("security", "STARTTLS");
        m.put("host", "smtp.gmail.com");
        m.put("port", 587);
        m.put("username", "vitc.academy@gmail.com");
        m.put("password", password);
        m.put("enabled", true);
        return m;
    }

    private String save(Map<String, Object> body, int expectedStatus) throws Exception {
        return mvc.perform(auth(put("/api/v1/admin/smtp-settings"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static void assertNoSecret(String body) {
        assertThat(body).doesNotContain(APP_PASSWORD_COMPACT).doesNotContain(APP_PASSWORD)
                .doesNotContain("\"password\"").doesNotContain("appPassword").doesNotContain("smtpPassword")
                .doesNotContain("encryptedPassword").doesNotContain("decryptedPassword");
    }

    @Test
    void providerRegistryAndGmailDetection() throws Exception {
        mvc.perform(auth(get("/api/v1/admin/smtp-settings/providers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("GMAIL"))
                .andExpect(jsonPath("$.data[0].label").value("Gmail / Google Workspace"))
                .andExpect(jsonPath("$.data[0].credentialLength").value(16));

        mvc.perform(auth(post("/api/v1/admin/smtp-settings/detect-provider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"KaustubhNGondhali1@gmail.com\",\"provider\":\"AUTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected").value(true))
                .andExpect(jsonPath("$.data.provider").value("GMAIL"))
                .andExpect(jsonPath("$.data.method").value("DOMAIN"))
                .andExpect(jsonPath("$.data.host").value("smtp.gmail.com"))
                .andExpect(jsonPath("$.data.port").value(587))
                .andExpect(jsonPath("$.data.security").value("STARTTLS"))
                .andExpect(jsonPath("$.data.username").value("kaustubhngondhali1@gmail.com"))
                // Sender details suggested from the address.
                .andExpect(jsonPath("$.data.senderName").isNotEmpty())
                .andExpect(jsonPath("$.data.replyToEmail").value("kaustubhngondhali1@gmail.com"))
                .andExpect(jsonPath("$.data.sendingDomain").value("gmail.com"));

        // Google Workspace on a custom domain, provider picked explicitly: Gmail SMTP settings.
        mvc.perform(auth(post("/api/v1/admin/email-config/detect-provider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"office@example.org\",\"provider\":\"GMAIL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.host").value("smtp.gmail.com"))
                .andExpect(jsonPath("$.data.username").value("office@example.org"));
    }

    @Test
    void savesEncryptedAppPasswordAndNeverReturnsIt() throws Exception {
        String saved = save(gmailBody(APP_PASSWORD), 200);
        assertNoSecret(saved);
        assertThat(saved).contains("Email configuration saved successfully");

        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(row.getEncryptedPassword()).doesNotContain(APP_PASSWORD_COMPACT);
        assertThat(cryptoService.decrypt(row.getEncryptedPassword())).isEqualTo(APP_PASSWORD_COMPACT);
        assertThat(row.getProvider()).isEqualTo("GMAIL");
        assertThat(row.getUpdatedBy()).isEqualTo(USER);

        for (String path : List.of("/api/v1/admin/smtp-settings", "/api/v1/admin/email-config")) {
            String body = mvc.perform(auth(get(path)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.configured").value(true))
                    .andExpect(jsonPath("$.data.active").value(true))
                    .andExpect(jsonPath("$.data.provider").value("GMAIL"))
                    .andExpect(jsonPath("$.data.security").value("STARTTLS"))
                    .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                    .andExpect(jsonPath("$.data.credentialStatus").value("App Password already configured"))
                    .andExpect(jsonPath("$.data.replyToEmail").value("support@vitc.in"))
                    .andExpect(jsonPath("$.data.dkimSelector").value("google"))
                    .andReturn().getResponse().getContentAsString();
            assertNoSecret(body);
        }

        List<AuditLog> created = auditLogRepository.findTop200ByActionOrderByIdDesc("SMTP_CONFIGURATION_CREATED");
        assertThat(created).isNotEmpty();
        assertThat(created.get(0).getActorIdentifier()).isEqualTo(USER);
        assertThat(created.get(0).getDescription()).doesNotContain(APP_PASSWORD_COMPACT);
    }

    /** Email-first flow: only the address + App Password are typed; Sender Name falls back to the default. */
    @Test
    void blankSenderNameUsesInstallationDefault() throws Exception {
        Map<String, Object> body = gmailBody(APP_PASSWORD);
        body.put("fromName", null);
        body.remove("replyToEmail");
        body.remove("sendingDomain");
        body.remove("dkimSelector");
        save(body, 200);
        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(row.getFromName()).isNotBlank();
        assertThat(row.getReplyToEmail()).isNull();
    }

    /** A saved credential that cannot be an App Password (e.g. a 2-character leftover) is flagged and cannot be kept. */
    @Test
    void invalidSavedCredentialIsFlaggedAndMustBeReplaced() throws Exception {
        settingsRepository.save(SmtpSetting.builder().settingsKey("DEFAULT").provider("GMAIL")
                .host("smtp.gmail.com").port(587).username("vitc.academy@gmail.com")
                .fromEmail("vitc.academy@gmail.com").fromName("VITC")
                .encryptedPassword(cryptoService.encrypt("pw")).build());

        String body = mvc.perform(auth(get("/api/v1/admin/smtp-settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(jsonPath("$.data.credentialValid").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andReturn().getResponse().getContentAsString();
        assertNoSecret(body);
        assertThat(body).doesNotContain("\"pw\"");

        // "Keep saved" is refused with a clear message...
        assertThat(save(gmailBody(null), 400)).contains("not a valid 16-character");
        // ...and a real App Password fixes it.
        save(gmailBody(APP_PASSWORD), 200);
        mvc.perform(auth(get("/api/v1/admin/smtp-settings")))
                .andExpect(jsonPath("$.data.credentialValid").value(true))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void replaceCredentialAndKeepCredential() throws Exception {
        save(gmailBody(APP_PASSWORD), 200);

        // Editing other settings with no credential keeps the stored one.
        Map<String, Object> edit = gmailBody(null);
        edit.put("fromName", "VITC Admissions");
        save(edit, 200);
        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(cryptoService.decrypt(row.getEncryptedPassword())).isEqualTo(APP_PASSWORD_COMPACT);
        assertThat(row.getFromName()).isEqualTo("VITC Admissions");

        // Replace Credential.
        String body = save(gmailBody("qrst uvwx yzab cdef"), 200);
        assertThat(body).doesNotContain("qrstuvwxyzabcdef");
        row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(cryptoService.decrypt(row.getEncryptedPassword())).isEqualTo("qrstuvwxyzabcdef");
        List<AuditLog> replaced = auditLogRepository.findTop200ByActionOrderByIdDesc("SMTP_CREDENTIAL_REPLACED");
        assertThat(replaced).isNotEmpty();
        assertThat(replaced.get(0).getDescription()).doesNotContain("qrstuvwxyzabcdef");
    }

    @Test
    void rejectsInvalidGmailAndTransportCombinations() throws Exception {
        // Normal Google password instead of a 16-character App Password - error never echoes it.
        String err = save(gmailBody("MyNormalPass#2024"), 400);
        assertThat(err).contains("16").doesNotContain("MyNormalPass#2024");

        Map<String, Object> wrongPort = gmailBody(APP_PASSWORD);
        wrongPort.put("port", 465); // STARTTLS on the implicit-TLS port
        assertThat(save(wrongPort, 400)).contains("465");

        Map<String, Object> wrongHost = gmailBody(APP_PASSWORD);
        wrongHost.put("host", "smtp.example.com");
        assertThat(save(wrongHost, 400)).contains("smtp.gmail.com");

        Map<String, Object> otherSender = gmailBody(APP_PASSWORD);
        otherSender.put("fromEmail", "someone.else@gmail.com");
        assertThat(save(otherSender, 400)).contains("must match");

        Map<String, Object> injection = gmailBody(APP_PASSWORD);
        injection.put("fromName", "VITC\r\nBcc: victim@example.com");
        save(injection, 400);

        Map<String, Object> badReplyTo = gmailBody(APP_PASSWORD);
        badReplyTo.put("replyToEmail", "not-an-email");
        save(badReplyTo, 400);

        assertThat(settingsRepository.findBySettingsKey("DEFAULT")).isEmpty();
    }

    @Test
    void emailConfigAliasIsAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/email-config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/email-config/providers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/email-config/detect-provider")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@gmail.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/email-config")
                        .header("X-Teacher-Username", "VITCteacher").header("X-Teacher-Token", "x"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testEmailWithoutConfigurationAndWithUnreachableServerFailsSafely() throws Exception {
        mvc.perform(auth(post("/api/v1/admin/smtp-settings/test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"owner@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No SMTP configuration")));

        settingsRepository.save(SmtpSetting.builder().settingsKey("DEFAULT").host("127.0.0.1").port(1)
                .username("vitc@example.com").encryptedPassword(cryptoService.encrypt("s3cret-credential"))
                .fromEmail("vitc@example.com").fromName("VITC").provider("CUSTOM").build());
        String body = mvc.perform(auth(post("/api/v1/admin/email-config/test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipient\":\"owner@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("s3cret-credential");
        List<AuditLog> tested = auditLogRepository.findTop200ByActionOrderByIdDesc("SMTP_CONFIGURATION_TESTED");
        assertThat(tested).isNotEmpty();
        assertThat(tested.get(0).getDescription()).contains("failed").doesNotContain("s3cret-credential");
    }

    @Test
    void disabledConfigurationIsNotUsedBySystemEmails() {
        settingsRepository.save(SmtpSetting.builder().settingsKey("DEFAULT").host("smtp.gmail.com").port(587)
                .username("vitc@gmail.com").encryptedPassword(cryptoService.encrypt(APP_PASSWORD_COMPACT))
                .fromEmail("vitc@gmail.com").fromName("VITC").enabled(false).build());
        // No MAIL_HOST fallback in the test profile, so nothing is configured for system emails.
        assertThat(emailService.isConfigured()).isFalse();
    }

    /** Sender Name and Reply-To reach the actual message headers (legacy-transport row, local fake SMTP). */
    @Test
    void senderNameAndReplyToAreAppliedToOutgoingMail() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<String> captured = CompletableFuture.supplyAsync(() -> fakeSmtp(server));
            settingsRepository.save(SmtpSetting.builder().settingsKey("DEFAULT").host("127.0.0.1")
                    .port(server.getLocalPort()).username("vitc@example.com")
                    .encryptedPassword(cryptoService.encrypt("local-credential"))
                    .fromEmail("vitc@example.com").fromName("Vandana IT Courses")
                    .replyToEmail("support@vitc.in").build());

            emailService.sendHtml("student@example.com", "Hello", "<p>Hi</p>");

            String data = captured.get(10, TimeUnit.SECONDS);
            assertThat(data).contains("From: Vandana IT Courses <vitc@example.com>");
            assertThat(data).contains("Reply-To: support@vitc.in");
            assertThat(data).doesNotContain("local-credential");
        }
    }

    /** Minimal SMTP server: no STARTTLS/AUTH advertised, returns the DATA section. */
    private static String fakeSmtp(ServerSocket server) {
        try (Socket s = server.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
            out.print("220 fake ESMTP\r\n");
            out.flush();
            StringBuilder data = new StringBuilder();
            String line;
            boolean inData = false;
            while ((line = in.readLine()) != null) {
                if (inData) {
                    if (line.equals(".")) {
                        inData = false;
                        out.print("250 OK\r\n");
                    } else {
                        data.append(line).append('\n');
                    }
                } else if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                    out.print("250 fake\r\n");
                } else if (line.startsWith("DATA")) {
                    inData = true;
                    out.print("354 go\r\n");
                } else if (line.startsWith("QUIT")) {
                    out.print("221 bye\r\n");
                    out.flush();
                    break;
                } else {
                    out.print("250 OK\r\n");
                }
                out.flush();
            }
            return data.toString();
        } catch (Exception e) {
            return "ERROR " + e;
        }
    }
}

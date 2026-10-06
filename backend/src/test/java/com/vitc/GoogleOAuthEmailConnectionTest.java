package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.AuditLog;
import com.vitc.entity.SmtpSetting;
import com.vitc.entity.enums.AdminRole;
import com.vitc.mail.google.GmailOAuthService;
import com.vitc.mail.google.GoogleAuthException;
import com.vitc.mail.google.GoogleOAuthClient;
import com.vitc.mail.google.GoogleTokens;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.AuditLogRepository;
import com.vitc.repository.SmtpSettingRepository;
import com.vitc.security.CryptoService;
import com.vitc.service.EmailService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * "Connect Google Account" (Gmail API over OAuth 2.0, no App Password): authorization URL,
 * signed single-use state on the public callback, encrypted refresh token, sending through the
 * Gmail API with the existing templates/headers, token refresh, revoked grants, disconnect, and
 * that no token or client secret ever reaches an API response. Google's HTTP endpoints are stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.google.client-id=test-client-id.apps.googleusercontent.com",
        "app.google.client-secret=test-client-secret-value",
        "app.google.redirect-uri=http://localhost:8080/api/v1/public/email/google/callback",
        "app.frontend-url=http://localhost:5500"
})
class GoogleOAuthEmailConnectionTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository adminRepository;
    @Autowired SmtpSettingRepository settingsRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CryptoService cryptoService;
    @Autowired EmailService emailService;
    @Autowired GmailOAuthService gmailOAuth;
    @Autowired ObjectMapper mapper;
    @SpyBean GoogleOAuthClient googleClient;

    private static final String USER = "google-oauth-admin";
    private static final String TOKEN = "test-google-oauth-token";
    private static final String ACCESS = "ya29.test-access-token";
    private static final String REFRESH = "1//test-refresh-token";
    private static final String SECRET = "test-client-secret-value";
    private static final String GMAIL = "vitc.academy@gmail.com";

    @BeforeEach
    void seed() {
        adminRepository.findByUsernameIgnoreCase(USER).ifPresent(adminRepository::delete);
        settingsRepository.findBySettingsKey("DEFAULT").ifPresent(settingsRepository::delete);
        gmailOAuth.invalidate();
        clearInvocations(googleClient);
        Admin admin = new Admin();
        admin.setUsername(USER);
        admin.setEmail(USER + "@vitc.in");
        admin.setFullName("Google OAuth Admin");
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

    private static GoogleTokens tokens(String access, String refresh, String email) {
        return new GoogleTokens(access, refresh, Instant.now().plusSeconds(3000),
                GoogleOAuthClient.SCOPE, email);
    }

    private static void assertNoSecrets(String body) {
        assertThat(body).doesNotContain(ACCESS).doesNotContain(REFRESH).doesNotContain(SECRET)
                .doesNotContain("refreshToken").doesNotContain("accessToken");
    }

    /** Clicks "Connect Google Account" and returns the state Google would echo back. */
    private String startConnect() throws Exception {
        String body = mvc.perform(auth(get("/api/v1/admin/smtp-settings/google/connect")).param("email", GMAIL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = mapper.readTree(body).path("data").path("authorizationUrl").asText();
        Matcher m = Pattern.compile("[?&]state=([^&]+)").matcher(url);
        assertThat(m.find()).isTrue();
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }

    private void connect() throws Exception {
        doReturn(tokens(ACCESS, REFRESH, GMAIL)).when(googleClient).exchangeCode("auth-code-1");
        String state = startConnect();
        mvc.perform(get("/api/v1/public/email/google/callback").param("code", "auth-code-1").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=connected"));
    }

    @Test
    void authorizationUrlAsksOnlyForSendPermissionAndCarriesNoSecret() throws Exception {
        String body = mvc.perform(auth(get("/api/v1/admin/email-config/google/connect")).param("email", GMAIL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = URLDecoder.decode(mapper.readTree(body).path("data").path("authorizationUrl").asText(),
                StandardCharsets.UTF_8);
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=test-client-id.apps.googleusercontent.com")
                .contains("https://www.googleapis.com/auth/gmail.send")
                .contains("access_type=offline")
                .contains("login_hint=" + GMAIL)
                .contains("state=")
                .doesNotContain(SECRET);

        mvc.perform(auth(get("/api/v1/admin/smtp-settings/google")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.redirectUri")
                        .value("http://localhost:8080/api/v1/public/email/google/callback"));
    }

    @Test
    void callbackStoresEncryptedRefreshTokenAndNeverExposesIt() throws Exception {
        connect();

        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(row.googleConnected()).isTrue();
        assertThat(row.getGoogleEmail()).isEqualTo(GMAIL);
        assertThat(row.getGoogleRefreshTokenEnc()).doesNotContain(REFRESH);
        assertThat(cryptoService.decrypt(row.getGoogleRefreshTokenEnc())).isEqualTo(REFRESH);
        assertThat(row.getGoogleConnectedBy()).isEqualTo(USER);

        String settings = mvc.perform(auth(get("/api/v1/admin/smtp-settings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleConnected").value(true))
                .andExpect(jsonPath("$.data.googleEmail").value(GMAIL))
                .andExpect(jsonPath("$.data.authMode").value("GMAIL_OAUTH"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString();
        assertNoSecrets(settings);
        String status = mvc.perform(auth(get("/api/v1/admin/smtp-settings/google")))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.email").value(GMAIL))
                .andReturn().getResponse().getContentAsString();
        assertNoSecrets(status);

        List<AuditLog> created = auditLogRepository.findTop200ByActionOrderByIdDesc("SMTP_CONFIGURATION_CREATED");
        assertThat(created).isNotEmpty();
        assertThat(created.get(0).getActorIdentifier()).isEqualTo(USER);
        assertThat(created.get(0).getDescription()).contains("Google account connected").doesNotContain(REFRESH);
    }

    @Test
    void forgedReplayedOrDeniedCallbacksDoNothing() throws Exception {
        doReturn(tokens(ACCESS, REFRESH, GMAIL)).when(googleClient).exchangeCode(anyString());

        mvc.perform(get("/api/v1/public/email/google/callback").param("code", "x").param("state", "forged.state"))
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=invalid_state"));
        mvc.perform(get("/api/v1/public/email/google/callback").param("code", "x"))
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=invalid_state"));
        mvc.perform(get("/api/v1/public/email/google/callback").param("error", "access_denied"))
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=denied"));
        verify(googleClient, never()).exchangeCode(anyString());

        String state = startConnect();
        mvc.perform(get("/api/v1/public/email/google/callback").param("code", "c1").param("state", state))
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=connected"));
        // The same state a second time (captured callback URL) is rejected.
        mvc.perform(get("/api/v1/public/email/google/callback").param("code", "c2").param("state", state))
                .andExpect(header().string("Location", "http://localhost:5500/admin/smtp-settings.html?google=invalid_state"));
        verify(googleClient, times(1)).exchangeCode(anyString());
    }

    @Test
    void systemEmailsGoThroughGmailApiWithSenderAndReplyTo() throws Exception {
        connect();
        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        row.setFromName("Vandana IT Courses");
        row.setReplyToEmail("support@vitc.in");
        settingsRepository.save(row);
        doNothing().when(googleClient).sendRaw(anyString(), any());

        assertThat(emailService.isConfigured()).isTrue();
        emailService.sendHtml("student@example.com", "Your VITC login", "<p>Welcome</p>");

        ArgumentCaptor<byte[]> raw = ArgumentCaptor.forClass(byte[].class);
        verify(googleClient).sendRaw(eq(ACCESS), raw.capture());
        String mime = new String(raw.getValue(), StandardCharsets.UTF_8);
        assertThat(mime).contains("From: Vandana IT Courses <" + GMAIL + ">")
                .contains("Reply-To: support@vitc.in")
                .contains("To: student@example.com")
                .contains("Subject: Your VITC login");
    }

    @Test
    void expiredAccessTokenIsRefreshedFromTheStoredRefreshToken() throws Exception {
        connect();
        gmailOAuth.invalidate(); // drop the in-memory access token, as after a restart
        doReturn(tokens("ya29.refreshed", null, null)).when(googleClient).refreshAccessToken(REFRESH);
        doNothing().when(googleClient).sendRaw(anyString(), any());

        mvc.perform(auth(post("/api/v1/admin/smtp-settings/test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"owner@example.com\"}"))
                .andExpect(status().isOk());
        verify(googleClient).refreshAccessToken(REFRESH);
        verify(googleClient).sendRaw(eq("ya29.refreshed"), any());
    }

    @Test
    void revokedGrantGivesAClearReconnectMessage() throws Exception {
        connect();
        doThrow(GoogleAuthException.revoked()).when(googleClient).sendRaw(anyString(), any());

        String body = mvc.perform(auth(post("/api/v1/admin/smtp-settings/test"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"owner@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("reconnect your Google account");
        assertNoSecrets(body);
    }

    @Test
    void disconnectRevokesAtGoogleAndFallsBackToSmtp() throws Exception {
        connect();
        doReturn(true).when(googleClient).revoke(REFRESH);

        mvc.perform(auth(post("/api/v1/admin/smtp-settings/google/disconnect")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Google account disconnected and access revoked at Google."))
                .andExpect(jsonPath("$.data.connected").value(false));
        verify(googleClient).revoke(REFRESH);

        SmtpSetting row = settingsRepository.findBySettingsKey("DEFAULT").orElseThrow();
        assertThat(row.googleConnected()).isFalse();
        assertThat(row.getGoogleRefreshTokenEnc()).isNull();
        // No SMTP credential saved either, so nothing is configured for system emails now.
        assertThat(emailService.isConfigured()).isFalse();
    }

    @Test
    void googleEndpointsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/smtp-settings/google")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/smtp-settings/google/connect")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/email-config/google/disconnect")).andExpect(status().isUnauthorized());
    }
}

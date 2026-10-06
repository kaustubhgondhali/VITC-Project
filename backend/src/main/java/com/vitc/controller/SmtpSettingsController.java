package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.SmtpProviderDetectRequest;
import com.vitc.dto.request.SmtpSettingsRequest;
import com.vitc.dto.request.TestEmailRequest;
import com.vitc.dto.response.GoogleConnectionResponse;
import com.vitc.dto.response.SmtpProviderDetectResponse;
import com.vitc.dto.response.SmtpProviderResponse;
import com.vitc.dto.response.SmtpSettingsResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.SmtpSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only Email / SMTP configuration.
 *
 * <p>Protected by {@code AdminAuthInterceptor} (registered in
 * {@code WebConfig} for every {@code /api/v1/admin/**} path) — the same
 * session token the rest of the Main Admin Panel already uses — plus the
 * MAIN_ADMIN role check. Served under the original {@code /smtp-settings}
 * path and the {@code /email-config} alias; both are admin-only. The SMTP
 * password is never returned by any endpoint here.</p>
 */
@Tag(name = "Admin SMTP Settings", description = "Configure the outgoing-mail (SMTP) server used by the platform")
@RestController
@RequestMapping({"/api/v1/admin/smtp-settings", "/api/v1/admin/email-config"})
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class SmtpSettingsController {

    private final SmtpSettingsService service;

    @Operation(summary = "Get the current SMTP configuration (password is never returned)")
    @GetMapping
    public ResponseEntity<ApiResponse<SmtpSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.getSettings()));
    }

    @Operation(summary = "Save the SMTP configuration (password is encrypted before storage)")
    @PutMapping
    public ResponseEntity<ApiResponse<SmtpSettingsResponse>> save(
            @Valid @RequestBody SmtpSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Email configuration saved successfully", service.save(request)));
    }

    @Operation(summary = "Save the SMTP configuration (alias of PUT)")
    @PostMapping
    public ResponseEntity<ApiResponse<SmtpSettingsResponse>> saveViaPost(
            @Valid @RequestBody SmtpSettingsRequest request) {
        return save(request);
    }

    @Operation(summary = "Send a real test email using the saved SMTP configuration")
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> test(@Valid @RequestBody TestEmailRequest request) {
        service.sendTestEmail(request);
        return ResponseEntity.ok(ApiResponse.ok("Email sent successfully", null));
    }

    @Operation(summary = "List the supported email providers")
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<SmtpProviderResponse>>> providers() {
        return ResponseEntity.ok(ApiResponse.ok(service.providers()));
    }

    @Operation(summary = "Detect the provider from the SMTP email address and return its standard settings")
    @PostMapping("/detect-provider")
    public ResponseEntity<ApiResponse<SmtpProviderDetectResponse>> detectProvider(
            @Valid @RequestBody SmtpProviderDetectRequest request) {
        SmtpProviderDetectResponse result = service.detectProvider(request);
        return ResponseEntity.ok(ApiResponse.ok(result.message(), result));
    }

    // ---- Connect Google Account (Gmail API over OAuth 2.0 - no App Password) ----------------
    // The matching callback is public (Google redirects the browser there with no admin headers):
    // see PublicGoogleOAuthCallbackController. None of these can return a token.

    @Operation(summary = "Google account connection status (never returns a token)")
    @GetMapping("/google")
    public ResponseEntity<ApiResponse<GoogleConnectionResponse>> googleStatus() {
        return ResponseEntity.ok(ApiResponse.ok(service.googleStatus()));
    }

    @Operation(summary = "URL of Google's sign-in / consent page for Connect Google Account")
    @GetMapping("/google/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> googleConnect(
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("authorizationUrl", service.googleAuthorizationUrl(email))));
    }

    @Operation(summary = "Disconnect the Google account (revokes access at Google)")
    @PostMapping("/google/disconnect")
    public ResponseEntity<ApiResponse<GoogleConnectionResponse>> googleDisconnect() {
        String message = service.googleDisconnect();
        return ResponseEntity.ok(ApiResponse.ok(message, service.googleStatus()));
    }
}

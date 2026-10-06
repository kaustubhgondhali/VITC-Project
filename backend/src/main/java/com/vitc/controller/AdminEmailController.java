package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.TestEmailRequest;
import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.security.RequireRole;
import com.vitc.security.Role;
import com.vitc.service.StudentCredentialEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only email monitoring. Lives under /api/v1/admin/** so the existing
 * AdminAuthInterceptor guards it; students can never reach these endpoints.
 */
@Tag(name = "Admin Emails", description = "Student credential email delivery status and retries")
@RestController
@RequestMapping("/api/v1/admin/emails")
@RequiredArgsConstructor
@RequireRole(Role.MAIN_ADMIN)
public class AdminEmailController {

    private final StudentCredentialEmailService service;

    @Operation(summary = "Is SMTP configured for this environment?")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("configured", service.isConfigured())));
    }

    @Operation(summary = "Recent credential email deliveries")
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<EmailDeliveryLogResponse>>> logs() {
        return ResponseEntity.ok(ApiResponse.ok(service.recentLogs()));
    }

    @Operation(summary = "Credential emails for one student")
    @GetMapping("/logs/student/{studentUserId}")
    public ResponseEntity<ApiResponse<List<EmailDeliveryLogResponse>>> studentLogs(@PathVariable Long studentUserId) {
        return ResponseEntity.ok(ApiResponse.ok(service.logsForStudent(studentUserId)));
    }

    @Operation(summary = "Retry a failed credential email (issues a fresh temporary password)")
    @PostMapping("/logs/{id}/retry")
    public ResponseEntity<ApiResponse<EmailDeliveryLogResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Retry attempted", service.retry(id)));
    }

    @Operation(summary = "Send a test email to verify SMTP settings")
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> test(@Valid @RequestBody TestEmailRequest request) {
        service.sendTestEmail(request.to());
        return ResponseEntity.ok(ApiResponse.ok("Test email sent to " + request.to(), null));
    }
}

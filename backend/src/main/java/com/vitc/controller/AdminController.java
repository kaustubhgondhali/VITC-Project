package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.AdminLoginRequest;
import com.vitc.dto.request.AdminRequest;
import com.vitc.dto.request.AdminUpdateRequest;
import com.vitc.dto.request.ForgotPasswordRequest;
import com.vitc.dto.request.PasswordChangeRequest;
import com.vitc.dto.request.ResetPasswordRequest;
import com.vitc.dto.request.SessionVerifyRequest;
import com.vitc.dto.response.AdminResponse;
import com.vitc.dto.response.DashboardStatsResponse;
import com.vitc.dto.response.PasswordResetTokenResponse;
import com.vitc.entity.enums.AdminRole;
import com.vitc.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin", description = "CRUD operations for admin accounts and dashboard statistics")
@RestController
@RequestMapping("/api/v1/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;
    private final com.vitc.service.PasswordRecoveryService passwordRecoveryService;

    @Operation(summary = "List all admins")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @Operation(summary = "Get an admin by id")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Admin found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Admin not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @Operation(summary = "Get an admin by username")
    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<AdminResponse>> byUsername(@PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByUsername(username)));
    }

    @Operation(summary = "Create an admin")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminResponse>> create(@Valid @RequestBody AdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Created successfully", service.create(request)));
    }

    @Operation(summary = "Update an admin")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminResponse>> update(@PathVariable Long id,
                                                             @Valid @RequestBody AdminUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Updated successfully", service.update(id, request)));
    }

    @Operation(summary = "Enable or disable an admin")
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<AdminResponse>> updateActive(@PathVariable Long id,
                                                                   @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", service.updateActive(id, active)));
    }

    @Operation(summary = "Change an admin password")
    @PatchMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Long id,
                                                            @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(id, request);
        return ResponseEntity.ok(ApiResponse.message("Password updated successfully"));
    }

    @Operation(summary = "Delete an admin")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Deleted successfully"));
    }

    @Operation(summary = "List active admins")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<AdminResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.ok(service.getActive()));
    }

    @Operation(summary = "List admins by role")
    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<AdminResponse>>> byRole(@PathVariable AdminRole role) {
        return ResponseEntity.ok(ApiResponse.ok(service.getByRole(role)));
    }

    @Operation(summary = "Verify admin credentials", description = "Validates username/password and stamps last login")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", service.login(request)));
    }

    @Operation(summary = "Verify an admin panel session token")
    @PostMapping("/session")
    public ResponseEntity<ApiResponse<AdminResponse>> verifySession(@Valid @RequestBody SessionVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Session valid", service.verifySession(request)));
    }

    @Operation(summary = "Invalidate an admin panel session token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody SessionVerifyRequest request) {
        service.logout(request);
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @Operation(summary = "Request an admin password recovery OTP",
            description = "Public. Sends a 6-digit verification OTP to the registered email or phone.")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> forgotPassword(
            @Valid @RequestBody com.vitc.dto.request.PasswordRecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists with the provided information, a verification OTP has been sent.",
                passwordRecoveryService.requestOtp(com.vitc.entity.enums.RecoveryPortal.ADMIN, request)));
    }

    @Operation(summary = "Verify admin password recovery OTP")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody com.vitc.dto.request.VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully",
                passwordRecoveryService.verifyOtp(com.vitc.entity.enums.RecoveryPortal.ADMIN, request)));
    }

    @Operation(summary = "Resend admin password recovery OTP")
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> resendOtp(
            @Valid @RequestBody com.vitc.dto.request.ResendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("A new OTP has been sent",
                passwordRecoveryService.resendOtp(com.vitc.entity.enums.RecoveryPortal.ADMIN, request)));
    }

    @Operation(summary = "Reset an admin password using verified reset token")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody com.vitc.dto.request.CompletePasswordResetRequest request) {
        passwordRecoveryService.resetPassword(com.vitc.entity.enums.RecoveryPortal.ADMIN, request);
        return ResponseEntity.ok(ApiResponse.message("Password reset successfully"));
    }

    @Operation(summary = "Aggregated dashboard statistics")
    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(service.dashboardStats()));
    }
}

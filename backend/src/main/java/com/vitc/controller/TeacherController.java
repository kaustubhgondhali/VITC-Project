package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.TeacherForgotPasswordRequest;
import com.vitc.dto.request.TeacherLoginRequest;
import com.vitc.dto.request.TeacherPasswordChangeRequest;
import com.vitc.dto.request.TeacherSelfResetPasswordRequest;
import com.vitc.dto.request.TeacherSessionVerifyRequest;
import com.vitc.dto.response.TeacherForgotPasswordResponse;
import com.vitc.dto.response.TeacherProfileResponse;
import com.vitc.dto.response.TeacherSessionResponse;
import com.vitc.security.TeacherAuthInterceptor;
import com.vitc.service.TeacherAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Teacher Admin authentication foundation. Completely separate from Main
 * Admin ({@code /api/v1/admins/**}) and Student ({@code /api/v1/student/**})
 * authentication - a Teacher session token only ever works here. Everything
 * except the login endpoint is protected by {@link TeacherAuthInterceptor};
 * the caller's identity always comes from the server-side session, never
 * from the request body.
 */
@Tag(name = "Teacher", description = "Teacher Admin authentication foundation")
@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherAccountService service;
    private final com.vitc.service.PasswordRecoveryService passwordRecoveryService;

    @Operation(summary = "Teacher Admin login (username + password)")
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> login(
            @Valid @RequestBody TeacherLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", service.login(request)));
    }

    @Operation(summary = "Verify a Teacher Admin session token")
    @PostMapping("/auth/session")
    public ResponseEntity<ApiResponse<TeacherProfileResponse>> verifySession(
            @Valid @RequestBody TeacherSessionVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Session valid", service.verifySession(request)));
    }

    @Operation(summary = "Teacher Admin logout")
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId) {
        service.logout(teacherId);
        return ResponseEntity.ok(ApiResponse.message("Logged out"));
    }

    @Operation(summary = "Authenticated teacher profile")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<TeacherProfileResponse>> me(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(service.profile(teacherId)));
    }

    @Operation(summary = "Change the teacher password (required after the first login)")
    @PostMapping("/auth/change-password")
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> changePassword(
            @RequestAttribute(TeacherAuthInterceptor.TEACHER_ID_ATTRIBUTE) Long teacherId,
            @Valid @RequestBody TeacherPasswordChangeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Password changed successfully", service.changePassword(teacherId, request)));
    }

    @Operation(summary = "Teacher Admin self-service forgot password OTP request")
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> forgotPassword(
            @Valid @RequestBody com.vitc.dto.request.PasswordRecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists with the provided information, a verification OTP has been sent.",
                passwordRecoveryService.requestOtp(com.vitc.entity.enums.RecoveryPortal.TEACHER, request)));
    }

    @Operation(summary = "Verify teacher password recovery OTP")
    @PostMapping("/auth/verify-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody com.vitc.dto.request.VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully",
                passwordRecoveryService.verifyOtp(com.vitc.entity.enums.RecoveryPortal.TEACHER, request)));
    }

    @Operation(summary = "Resend teacher password recovery OTP")
    @PostMapping("/auth/resend-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> resendOtp(
            @Valid @RequestBody com.vitc.dto.request.ResendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("A new OTP has been sent",
                passwordRecoveryService.resendOtp(com.vitc.entity.enums.RecoveryPortal.TEACHER, request)));
    }

    @Operation(summary = "Teacher Admin self-service reset password")
    @PostMapping("/auth/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody com.vitc.dto.request.CompletePasswordResetRequest request) {
        passwordRecoveryService.resetPassword(com.vitc.entity.enums.RecoveryPortal.TEACHER, request);
        return ResponseEntity.ok(ApiResponse.message("Password reset successfully. You can now log in with your new password."));
    }
}

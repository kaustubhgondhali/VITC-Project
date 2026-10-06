package com.vitc.controller;

import com.vitc.common.ApiResponse;
import com.vitc.dto.request.StudentForgotPasswordRequest;
import com.vitc.dto.request.StudentLoginRequest;
import com.vitc.dto.request.StudentPasswordChangeRequest;
import com.vitc.dto.request.StudentProfileImageUpdateRequest;
import com.vitc.dto.request.StudentProfileUpdateRequest;
import com.vitc.dto.request.StudentResetPasswordRequest;
import com.vitc.dto.response.StudentForgotPasswordResponse;
import com.vitc.dto.response.StudentProfileResponse;
import com.vitc.dto.response.StudentSessionResponse;
import com.vitc.security.StudentAuthInterceptor;
import com.vitc.service.StudentAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student portal API. Everything except the login endpoint is protected by
 * {@link StudentAuthInterceptor}; the caller's identity always comes from the
 * server-side session, never from the request body.
 */
@Tag(name = "Student", description = "Student portal authentication and profile")
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentAccountService service;
    private final com.vitc.service.PasswordRecoveryService passwordRecoveryService;

    @Operation(summary = "Student login (Student ID + password)")
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<StudentSessionResponse>> login(
            @Valid @RequestBody StudentLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", service.login(request)));
    }

    @Operation(summary = "Request a student password recovery OTP",
            description = "Public, unauthenticated. Emails/SMS a 6-digit one-time reset OTP to the account's registered contact.")
    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> forgotPassword(
            @Valid @RequestBody com.vitc.dto.request.PasswordRecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists with the provided information, a verification OTP has been sent.",
                passwordRecoveryService.requestOtp(com.vitc.entity.enums.RecoveryPortal.STUDENT, request)));
    }

    @Operation(summary = "Verify student password recovery OTP")
    @PostMapping("/auth/verify-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody com.vitc.dto.request.VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully",
                passwordRecoveryService.verifyOtp(com.vitc.entity.enums.RecoveryPortal.STUDENT, request)));
    }

    @Operation(summary = "Resend student password recovery OTP")
    @PostMapping("/auth/resend-otp")
    public ResponseEntity<ApiResponse<com.vitc.dto.response.PasswordRecoveryResponse>> resendOtp(
            @Valid @RequestBody com.vitc.dto.request.ResendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("A new OTP has been sent",
                passwordRecoveryService.resendOtp(com.vitc.entity.enums.RecoveryPortal.STUDENT, request)));
    }

    @Operation(summary = "Reset a student password using the verified reset token")
    @PostMapping("/auth/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody com.vitc.dto.request.CompletePasswordResetRequest request) {
        passwordRecoveryService.resetPassword(com.vitc.entity.enums.RecoveryPortal.STUDENT, request);
        return ResponseEntity.ok(ApiResponse.message("Password reset successfully"));
    }

    @Operation(summary = "Student logout")
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId) {
        service.logout(studentId);
        return ResponseEntity.ok(ApiResponse.message("Logged out"));
    }

    @Operation(summary = "Authenticated student profile and enrolled courses")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> me(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.profile(studentId)));
    }

    @Operation(summary = "Change the student password (required after the first login)")
    @PostMapping("/auth/change-password")
    public ResponseEntity<ApiResponse<StudentSessionResponse>> changePassword(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @Valid @RequestBody StudentPasswordChangeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Password changed successfully", service.changePassword(studentId, request)));
    }

    @Operation(summary = "Update the caller's own editable profile fields (full name, phone, city)")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateProfile(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @Valid @RequestBody StudentProfileUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Profile updated successfully", service.updateProfile(studentId, request)));
    }

    @Operation(summary = "Attach an image already uploaded via POST /api/v1/files as the profile picture")
    @PutMapping("/profile/image")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateProfileImage(
            @RequestAttribute(StudentAuthInterceptor.STUDENT_ID_ATTRIBUTE) Long studentId,
            @Valid @RequestBody StudentProfileImageUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Profile picture updated successfully", service.updateProfileImage(studentId, request)));
    }
}

package com.vitc.service;

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
import java.util.List;

public interface AdminService {

    List<AdminResponse> getAll();

    AdminResponse getById(Long id);

    AdminResponse getByUsername(String username);

    AdminResponse create(AdminRequest request);

    AdminResponse update(Long id, AdminUpdateRequest request);

    void delete(Long id);

    List<AdminResponse> getActive();

    List<AdminResponse> getByRole(AdminRole role);

    AdminResponse updateActive(Long id, boolean active);

    void changePassword(Long id, PasswordChangeRequest request);

    AdminResponse login(AdminLoginRequest request);

    AdminResponse verifySession(SessionVerifyRequest request);

    void logout(SessionVerifyRequest request);

    PasswordResetTokenResponse forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    DashboardStatsResponse dashboardStats();
}

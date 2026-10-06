package com.vitc.service;

import com.vitc.dto.request.TeacherLoginRequest;
import com.vitc.dto.request.TeacherPasswordChangeRequest;
import com.vitc.dto.request.TeacherSessionVerifyRequest;
import com.vitc.dto.request.TeacherForgotPasswordRequest;
import com.vitc.dto.request.TeacherSelfResetPasswordRequest;
import com.vitc.dto.response.TeacherForgotPasswordResponse;
import com.vitc.dto.response.TeacherProfileResponse;
import com.vitc.dto.response.TeacherSessionResponse;

/**
 * Teacher Admin authentication foundation: login, logout, profile and the
 * forced first-login password change. Deliberately scoped to authentication
 * only - course/content/student management for teachers is out of scope for
 * this part.
 */
public interface TeacherAccountService {

    TeacherSessionResponse login(TeacherLoginRequest request);

    /** Server-side session check used by the login page to drop stale local sessions. */
    TeacherProfileResponse verifySession(TeacherSessionVerifyRequest request);

    void logout(Long teacherId);

    TeacherProfileResponse profile(Long teacherId);

    TeacherSessionResponse changePassword(Long teacherId, TeacherPasswordChangeRequest request);

    TeacherForgotPasswordResponse forgotPassword(TeacherForgotPasswordRequest request);

    void resetPassword(TeacherSelfResetPasswordRequest request);
}

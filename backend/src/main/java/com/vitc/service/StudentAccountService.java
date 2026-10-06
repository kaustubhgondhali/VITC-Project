package com.vitc.service;

import com.vitc.dto.request.StudentForgotPasswordRequest;
import com.vitc.dto.request.StudentLoginRequest;
import com.vitc.dto.request.StudentPasswordChangeRequest;
import com.vitc.dto.request.StudentProfileImageUpdateRequest;
import com.vitc.dto.request.StudentProfileUpdateRequest;
import com.vitc.dto.request.StudentResetPasswordRequest;
import com.vitc.dto.response.StudentCredentialsResponse;
import com.vitc.dto.response.StudentForgotPasswordResponse;
import com.vitc.dto.response.StudentProfileResponse;
import com.vitc.dto.response.StudentSessionResponse;
import com.vitc.entity.PaymentOrder;

/**
 * Student portal accounts. Accounts are provisioned ONLY from a payment that
 * the backend itself has verified; nothing here is reachable from the buy /
 * checkout screens.
 */
public interface StudentAccountService {

    /**
     * Called once a course order is verified as PAID. Creates the student
     * account on first purchase (or reuses the existing one) and links the
     * enrolment to it.
     *
     * @return one-time credentials when a brand new account was created,
     *         otherwise a response with no password (existing account).
     */
    StudentCredentialsResponse provisionForPaidOrder(PaymentOrder order);

    StudentSessionResponse login(StudentLoginRequest request);

    void logout(Long studentId);

    StudentProfileResponse profile(Long studentId);

    StudentSessionResponse changePassword(Long studentId, StudentPasswordChangeRequest request);

    /**
     * Part 6 - update the caller's own editable profile fields (full name,
     * phone, city). {@code studentId} always comes from the authenticated
     * session (see {@code StudentAuthInterceptor}), never from the request
     * body, so a student can never target another account.
     */
    StudentProfileResponse updateProfile(Long studentId, StudentProfileUpdateRequest request);

    /** Part 6 - attach an already-uploaded image URL as the profile picture. */
    StudentProfileResponse updateProfileImage(Long studentId, StudentProfileImageUpdateRequest request);

    /**
     * Part 4 - starts a student password recovery. Looks the account up by
     * Student ID or email, scoped to {@code role = STUDENT} so this can never
     * touch a Main Admin or Teacher account. Always issues a fresh single-use
     * code (any earlier unused code for the same account is invalidated) and
     * emails it to the account's own registered address - the code itself is
     * never returned to the caller.
     */
    StudentForgotPasswordResponse forgotPassword(StudentForgotPasswordRequest request);

    /**
     * Part 4 - completes a student password recovery. The reset code alone
     * identifies the account (unique, role-scoped, time-limited, single-use).
     * Also clears any active session on the account, forcing a fresh sign-in
     * with the new password everywhere.
     */
    void resetPassword(StudentResetPasswordRequest request);
}

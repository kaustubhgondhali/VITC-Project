package com.vitc.service;

import com.vitc.dto.request.CompletePasswordResetRequest;
import com.vitc.dto.request.PasswordRecoveryRequest;
import com.vitc.dto.request.ResendOtpRequest;
import com.vitc.dto.request.VerifyOtpRequest;
import com.vitc.dto.response.PasswordRecoveryResponse;
import com.vitc.dto.response.VerifyOtpResponse;
import com.vitc.entity.enums.RecoveryPortal;

public interface PasswordRecoveryService {

    /**
     * Initiates the OTP-based password recovery flow for a specific portal.
     * Generates a 6-digit numeric OTP on the server, hashes and stores it,
     * and sends it to the user's registered contact.
     */
    PasswordRecoveryResponse requestOtp(RecoveryPortal portal, PasswordRecoveryRequest request);

    /**
     * Verifies the 6-digit OTP against the stored hash and enforces expiration and attempt limits.
     * Returns a short-lived reset authorization token upon success.
     */
    VerifyOtpResponse verifyOtp(RecoveryPortal portal, VerifyOtpRequest request);

    /**
     * Enforces the 60-second cooldown, generates a new OTP, and resends it.
     */
    PasswordRecoveryResponse resendOtp(RecoveryPortal portal, ResendOtpRequest request);

    /**
     * Completes the password reset using the verified reset authorization token,
     * hashes the new password with BCrypt, updates the account, and invalidates the session.
     */
    void resetPassword(RecoveryPortal portal, CompletePasswordResetRequest request);
}


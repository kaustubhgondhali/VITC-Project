package com.vitc.service;

public interface SmsService {

    /**
     * Checks if an external SMS provider (e.g. Twilio, AWS SNS, Msg91) is configured.
     */
    boolean isConfigured();

    /**
     * Sends an OTP SMS to the target phone number.
     *
     * @param phoneNumber recipient phone number (e.g. +91 9876543210)
     * @param otp the 6-digit one-time password
     * @param expiresInMinutes the OTP validity duration
     */
    void sendOtp(String phoneNumber, String otp, int expiresInMinutes);
}


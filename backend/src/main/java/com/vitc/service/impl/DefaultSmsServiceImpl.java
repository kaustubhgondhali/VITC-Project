package com.vitc.service.impl;

import com.vitc.exception.BadRequestException;
import com.vitc.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Clean SMS provider integration point.
 * Separates phone recovery delivery from core OTP generation and verification logic.
 */
@Slf4j
@Service
public class DefaultSmsServiceImpl implements SmsService {

    private final String providerApiKey;

    public DefaultSmsServiceImpl(@Value("${app.sms.api-key:}") String providerApiKey) {
        this.providerApiKey = providerApiKey == null ? "" : providerApiKey.trim();
    }

    @Override
    public boolean isConfigured() {
        return !providerApiKey.isBlank();
    }

    @Override
    public void sendOtp(String phoneNumber, String otp, int expiresInMinutes) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("Invalid recipient phone number");
        }
        if (!isConfigured()) {
            log.warn("SMS provider not configured (app.sms.api-key is empty). Generated OTP for [{}]: {}",
                    maskPhone(phoneNumber), otp);
            return;
        }
        // When configured, integrate external SMS gateway here.
        log.info("Dispatching SMS OTP to {}", maskPhone(phoneNumber));
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "******" + phone.substring(phone.length() - 4);
    }
}


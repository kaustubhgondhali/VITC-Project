package com.vitc.security.ratelimit;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * PART 2A/7 - the single list of abuse-prone endpoints this project protects.
 * Kept separate from {@link RateLimitingFilter} so the "what is protected"
 * list stays easy to scan and extend without touching the enforcement logic.
 */
@Component
public class RateLimitRuleRegistry {

    private final List<RateLimitRule> rules = List.of(

            // ----- Login (5/min/IP, approx.) -----
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/login",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getLogin, "student-login"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/login",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getLogin, "teacher-login"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/admins/login",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getLogin, "admin-login"),

            // ----- Forgot password & OTP (3/10min/identifier, approx.) -----
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/forgot-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "student-forgot-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/forgot-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "teacher-forgot-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/admins/forgot-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "admin-forgot-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/resend-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "student-resend-otp"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/resend-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "teacher-resend-otp"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/admins/resend-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "admin-resend-otp"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/verify-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "student-verify-otp"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/verify-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "teacher-verify-otp"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/admins/verify-otp",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getForgotPassword,
                    "admin-verify-otp"),

            // ----- Reset / change password -----
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/reset-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getResetPassword,
                    "student-reset-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/reset-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getResetPassword,
                    "teacher-reset-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/admins/reset-password",
                    RateLimitRule.KeyType.IP_AND_IDENTIFIER, RateLimitProperties::getResetPassword,
                    "admin-reset-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/student/auth/change-password",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getResetPassword, "student-change-password"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/teacher/auth/change-password",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getResetPassword, "teacher-change-password"),

            // ----- Public write forms -----
            new RateLimitRule(HttpMethod.POST, "/api/v1/contact-messages",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPublicWrite, "contact-form"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/reviews",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPublicWrite, "review-submission"),
            // Job Portal employer enquiry: every submission emails the owner, so keep floods out of the inbox.
            new RateLimitRule(HttpMethod.POST, "/api/v1/employer/enquiries",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPublicWrite, "employer-enquiry"),
            // Job Portal "Apply": multipart (resume) - keyed by IP only, so the body is never buffered.
            new RateLimitRule(HttpMethod.POST, "/api/v1/jobs/*/applications",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPublicWrite, "job-application"),

            // ----- Payment-related -----
            new RateLimitRule(HttpMethod.POST, "/api/v1/checkout/orders",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPayment, "checkout-create-order"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/checkout/orders/*/pay",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPayment, "checkout-pay"),
            new RateLimitRule(HttpMethod.POST, "/api/v1/checkout/orders/*/confirm",
                    RateLimitRule.KeyType.IP, RateLimitProperties::getPayment, "checkout-confirm")
    );

    public List<RateLimitRule> rules() {
        return rules;
    }
}

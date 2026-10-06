package com.vitc.service;

import com.vitc.dto.response.EmailDeliveryLogResponse;
import com.vitc.dto.response.StudentCredentialsResponse;
import com.vitc.entity.PaymentOrder;
import java.util.List;

/**
 * Sends the "your student account is ready" email. It is invoked ONLY from the
 * backend, and only after a payment has been verified and the student account
 * provisioned. Nothing here can be triggered from the public website.
 */
public interface StudentCredentialEmailService {

    /**
     * Fire-and-forget: never throws, so a mail problem can never fail or roll
     * back a verified payment. Every attempt is written to email_delivery_logs.
     */
    void sendForPaidOrder(PaymentOrder order, StudentCredentialsResponse credentials);

    /** Admin: recent delivery attempts (newest first). */
    List<EmailDeliveryLogResponse> recentLogs();

    /** Admin: delivery attempts for one student. */
    List<EmailDeliveryLogResponse> logsForStudent(Long studentUserId);

    /**
     * Admin retry. For a credentials email a brand new temporary password is
     * issued (the old one is never stored, so it cannot be re-sent).
     */
    EmailDeliveryLogResponse retry(Long logId);

    /** Admin SMTP smoke test. */
    void sendTestEmail(String to);

    boolean isConfigured();
}

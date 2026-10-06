package com.vitc.service;

import java.util.Optional;

/**
 * Low level SMTP sender. Credentials come from configuration/environment only;
 * nothing here is reachable from the browser.
 */
public interface EmailService {

    /** True when MAIL_HOST/MAIL_USERNAME are configured for this environment. */
    boolean isConfigured();

    /**
     * Sends an HTML email.
     *
     * @throws com.vitc.exception.BadRequestException with a user friendly,
     *         credential-free message when delivery fails.
     */
    void sendHtml(String to, String subject, String html);

    /**
     * Sends an HTML email through the admin-saved SMTP configuration only - no
     * environment fallback, and regardless of its enabled switch - so the Email
     * Configuration "Send Test Email" button proves exactly what was saved.
     *
     * @throws com.vitc.exception.BadRequestException with a credential-free message
     *         when nothing is saved or delivery fails.
     */
    default void sendHtmlWithSavedSettings(String to, String subject, String html) {
        sendHtml(to, subject, html);
    }

    /**
     * The business's own mailbox from the active email configuration - the Reply-To address
     * if one is set, otherwise the sending address - used for owner notifications such as new
     * enquiries. Empty when email is not configured.
     */
    default Optional<String> ownerInbox() {
        return Optional.empty();
    }
}

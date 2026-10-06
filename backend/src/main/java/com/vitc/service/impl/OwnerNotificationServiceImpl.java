package com.vitc.service.impl;

import com.vitc.entity.ContactMessage;
import com.vitc.entity.EmployerEnquiry;
import com.vitc.service.EmailService;
import com.vitc.service.OwnerNotificationService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class OwnerNotificationServiceImpl implements OwnerNotificationService {

    private final EmailService emailService;
    private final String configuredRecipients;
    private final String frontendUrl;

    public OwnerNotificationServiceImpl(EmailService emailService,
                                        @Value("${app.notifications.owner-emails:}") String configuredRecipients,
                                        @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.emailService = emailService;
        this.configuredRecipients = configuredRecipients == null ? "" : configuredRecipients;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void employerEnquiryReceived(EmployerEnquiry e) {
        if (e == null) {
            return;
        }
        String reference = enquiryReference(e.getId());
        String subject = "New employer enquiry: " + e.getCompanyName() + " - " + e.getJobTitle();
        String html = StudentEmailTemplates.employerEnquiryNotification(reference, e.getCompanyName(),
                e.getContactPerson(), e.getEmail(), e.getPhone(), whatsappNumber(e.getPhone()), e.getJobTitle(),
                e.getOpenings() == null ? null : String.valueOf(e.getOpenings()), e.getExperienceRequired(),
                e.getLocation(), e.getRequiredSkills(), e.getMessage(), frontendUrl + "/admin/job-portal.html");
        // Mailed only once the enquiry is committed, so the owner never hears about one that was rolled back.
        afterCommit(() -> {
            try {
                List<String> recipients = recipients();
                if (recipients.isEmpty()) {
                    log.warn("Employer enquiry {} saved, but no owner email is set up (Email / SMTP Settings "
                            + "or app.notifications.owner-emails)", reference);
                    return;
                }
                for (String to : recipients) {
                    try {
                        emailService.sendHtml(to, subject, html);
                        log.info("Employer enquiry {} emailed to {}", reference, to);
                    } catch (Exception ex) {
                        log.warn("Employer enquiry {} could not be emailed to {}: {}", reference, to, ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("Employer enquiry {} notification failed: {}", reference, ex.getMessage());
            }
        });
    }

    @Override
    public void contactMessageReceived(ContactMessage m) {
        if (m == null) {
            return;
        }
        String reference = enquiryReference(m.getId());
        String subject = "New website enquiry: " + m.getName() + (m.getSubject() != null && !m.getSubject().isBlank() ? " - " + m.getSubject() : "");
        String html = StudentEmailTemplates.contactMessageNotification(reference, m.getName(),
                m.getEmail(), m.getPhone(), whatsappNumber(m.getPhone()), m.getSubject(),
                m.getMessage(), frontendUrl + "/admin/dashboard.html");
        // Mailed only once the enquiry is committed, so the owner never hears about one that was rolled back.
        afterCommit(() -> {
            try {
                List<String> recipients = recipients();
                if (recipients.isEmpty()) {
                    log.warn("Contact enquiry {} saved, but no owner email is set up (Email / SMTP Settings "
                            + "or app.notifications.owner-emails)", reference);
                    return;
                }
                for (String to : recipients) {
                    try {
                        emailService.sendHtml(to, subject, html);
                        log.info("Contact enquiry {} emailed to {}", reference, to);
                    } catch (Exception ex) {
                        log.warn("Contact enquiry {} could not be emailed to {}: {}", reference, to, ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("Contact enquiry {} notification failed: {}", reference, ex.getMessage());
            }
        });
    }

    /** Explicitly configured owner addresses win; otherwise the business mailbox from Email / SMTP Settings. */
    private List<String> recipients() {
        List<String> configured = Arrays.stream(configuredRecipients.split("[,;]"))
                .map(String::trim)
                .filter(s -> s.contains("@"))
                .distinct()
                .toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return emailService.ownerInbox().map(List::of).orElse(List.of());
    }

    /** Shown to the visitor after submitting and repeated in their WhatsApp message - same format. */
    static String enquiryReference(Long id) {
        return id == null ? "EQ-NEW" : String.format(Locale.ENGLISH, "EQ-%05d", id);
    }

    /**
     * International digits for a wa.me link to the enquirer: Indian numbers are usually typed
     * without the country code, so a bare 10-digit number (or 0 + 10 digits) gets 91 prepended.
     */
    static String whatsappNumber(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits.length() >= 8 ? digits : null;
    }

    /** Runs once the surrounding transaction has committed - immediately when there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

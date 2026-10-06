package com.vitc.service;

import com.vitc.entity.ContactMessage;
import com.vitc.entity.EmployerEnquiry;

/**
 * Tells the business owner about things that need a human response. Email only: WhatsApp is
 * handled in the visitor's browser with a click-to-chat link (no WhatsApp API involved).
 */
public interface OwnerNotificationService {

    /**
     * Emails the owner a new employer enquiry once it has been saved. Fire-and-forget: never
     * throws, so a mail problem can never lose the enquiry itself.
     */
    void employerEnquiryReceived(EmployerEnquiry enquiry);

    /**
     * Emails the owner a new student / course contact enquiry once it has been saved.
     * Fire-and-forget: never throws, so a mail problem can never lose the enquiry itself.
     */
    void contactMessageReceived(ContactMessage message);
}

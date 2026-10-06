package com.vitc.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vitc.entity.ContactMessage;
import com.vitc.entity.EmployerEnquiry;
import com.vitc.exception.BadRequestException;
import com.vitc.service.EmailService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Owner email for a new employer enquiry: who receives it, what it says, and that it never throws. */
class OwnerNotificationServiceImplTest {

    private final EmailService email = mock(EmailService.class);

    private static EmployerEnquiry enquiry() {
        EmployerEnquiry e = EmployerEnquiry.builder().companyName("Acme <Labs>").contactPerson("Rahul Mehta")
                .email("rahul@acme.test").phone("98765 43210").jobTitle("Java Developer").openings(3)
                .requiredSkills("Java, Spring Boot").experienceRequired("0-2 years").location("Uran")
                .message("Need two freshers\nand one senior").build();
        e.setId(12L);
        return e;
    }

    private static ContactMessage contactMessage() {
        ContactMessage m = ContactMessage.builder().name("Sneha Patil").email("sneha@patil.test")
                .phone("98765 43210").subject("Full Stack Development")
                .message("I want to know about course fees\nand timings").build();
        m.setId(45L);
        return m;
    }

    @Test
    void emailsTheBusinessMailboxFromEmailSettings_byDefault() {
        when(email.ownerInbox()).thenReturn(Optional.of("owner@vitc.test"));
        new OwnerNotificationServiceImpl(email, "", "http://site.test/").employerEnquiryReceived(enquiry());

        verify(email).sendHtml(eq("owner@vitc.test"), eq("New employer enquiry: Acme <Labs> - Java Developer"),
                contains("EQ-00012"));
        verify(email).sendHtml(anyString(), anyString(), contains("https://wa.me/919876543210"));
        verify(email).sendHtml(anyString(), anyString(), contains("mailto:rahul@acme.test"));
        verify(email).sendHtml(anyString(), anyString(), contains("Acme &lt;Labs&gt;")); // escaped, never raw HTML
        verify(email).sendHtml(anyString(), anyString(), contains("Need two freshers<br>and one senior"));
        verify(email).sendHtml(anyString(), anyString(), contains("http://site.test/admin/job-portal.html"));
    }

    @Test
    void emailsTheOwnerWhenContactMessageReceived() {
        when(email.ownerInbox()).thenReturn(Optional.of("owner@vitc.test"));
        new OwnerNotificationServiceImpl(email, "", "http://site.test/").contactMessageReceived(contactMessage());

        verify(email).sendHtml(eq("owner@vitc.test"), eq("New website enquiry: Sneha Patil - Full Stack Development"),
                contains("EQ-00045"));
        verify(email).sendHtml(anyString(), anyString(), contains("https://wa.me/919876543210"));
        verify(email).sendHtml(anyString(), anyString(), contains("mailto:sneha@patil.test"));
        verify(email).sendHtml(anyString(), anyString(), contains("Sneha Patil"));
        verify(email).sendHtml(anyString(), anyString(), contains("Full Stack Development"));
        verify(email).sendHtml(anyString(), anyString(), contains("I want to know about course fees<br>and timings"));
        verify(email).sendHtml(anyString(), anyString(), contains("http://site.test/admin/dashboard.html"));
    }

    @Test
    void configuredOwnerAddressesWin_overTheEmailSettingsMailbox() {
        new OwnerNotificationServiceImpl(email, " hr@vitc.test, boss@vitc.test ; not-an-email ", "")
                .employerEnquiryReceived(enquiry());

        verify(email).sendHtml(eq("hr@vitc.test"), anyString(), anyString());
        verify(email).sendHtml(eq("boss@vitc.test"), anyString(), anyString());
        verify(email, times(2)).sendHtml(anyString(), anyString(), anyString());
        verify(email, never()).ownerInbox();
    }

    @Test
    void mailProblemsNeverEscape_andNothingIsSentWithoutARecipient() {
        when(email.ownerInbox()).thenReturn(Optional.of("owner@vitc.test"));
        doThrow(new BadRequestException("Email is not configured")).when(email).sendHtml(anyString(), anyString(), anyString());
        assertThatCode(() -> new OwnerNotificationServiceImpl(email, "", "").employerEnquiryReceived(enquiry()))
                .doesNotThrowAnyException();
        assertThatCode(() -> new OwnerNotificationServiceImpl(email, "", "").contactMessageReceived(contactMessage()))
                .doesNotThrowAnyException();

        EmailService unconfigured = mock(EmailService.class);
        when(unconfigured.ownerInbox()).thenReturn(Optional.empty());
        new OwnerNotificationServiceImpl(unconfigured, "", "").employerEnquiryReceived(enquiry());
        new OwnerNotificationServiceImpl(unconfigured, "", "").contactMessageReceived(contactMessage());
        verify(unconfigured, never()).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void whatsappNumbersAndReferences_areNormalised() {
        assertThat(OwnerNotificationServiceImpl.whatsappNumber("98765 43210")).isEqualTo("919876543210");
        assertThat(OwnerNotificationServiceImpl.whatsappNumber("09876543210")).isEqualTo("919876543210");
        assertThat(OwnerNotificationServiceImpl.whatsappNumber("+91 98765-43210")).isEqualTo("919876543210");
        assertThat(OwnerNotificationServiceImpl.whatsappNumber("+44 20 7946 0958")).isEqualTo("442079460958");
        assertThat(OwnerNotificationServiceImpl.whatsappNumber("12")).isNull();
        assertThat(OwnerNotificationServiceImpl.enquiryReference(12L)).isEqualTo("EQ-00012");
    }
}

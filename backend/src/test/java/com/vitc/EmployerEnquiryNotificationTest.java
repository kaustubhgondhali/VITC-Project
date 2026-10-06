package com.vitc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.service.EmailService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Job Portal employer enquiry -> saved -> owner emailed (after commit) with the enquiry details. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployerEnquiryNotificationTest {

    @Autowired MockMvc mvc;
    @MockBean EmailService emailService;

    @Test
    void submittedEnquiry_isSaved_andEmailedToTheOwner() throws Exception {
        when(emailService.ownerInbox()).thenReturn(Optional.of("owner@vitc.test"));

        mvc.perform(post("/api/v1/employer/enquiries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"companyName":"Bright Hire Pvt Ltd","contactPerson":"Anita Rao","email":"anita@brighthire.test",
                         "phone":"9876543210","jobTitle":"QA Engineer","openings":2,"experienceRequired":"1-3 years",
                         "location":"Navi Mumbai","requiredSkills":"Selenium, Java","message":"Hiring two testers"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber());

        verify(emailService).sendHtml(eq("owner@vitc.test"),
                eq("New employer enquiry: Bright Hire Pvt Ltd - QA Engineer"), contains("Anita Rao"));
        verify(emailService).sendHtml(anyString(), anyString(), contains("Hiring two testers"));
        verify(emailService).sendHtml(anyString(), anyString(), contains("https://wa.me/919876543210"));
    }

    @Test
    void invalidEnquiry_isRejected_andNobodyIsEmailed() throws Exception {
        mvc.perform(post("/api/v1/employer/enquiries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"No Details Ltd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verify(emailService, never()).sendHtml(anyString(), anyString(), anyString());
    }
}

package com.vitc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vitc.entity.InternshipApplication;
import com.vitc.repository.InternshipApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternshipApplicationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired InternshipApplicationRepository applications;

    @Test
        void publicSubmissionIsPersistedWithAllApplicationFields() throws Exception {
        mvc.perform(post("/api/v1/internships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test Student","email":"test@example.com","phone":"9999999999",
                                 "college":"Test College","domain":"Java Full Stack","duration":"3 Months",
                                 "resumeUrl":null,"message":"Interested in internship."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Test Student"));

        InternshipApplication saved = applications.findAll().stream()
                .filter(application -> "test@example.com".equals(application.getEmail()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", saved.getStatus().name());
        org.junit.jupiter.api.Assertions.assertEquals("Test Student", saved.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals("Test College", saved.getCollege());
        org.junit.jupiter.api.Assertions.assertEquals("3 Months", saved.getDuration());
        org.junit.jupiter.api.Assertions.assertEquals("Interested in internship.", saved.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals("Java Full Stack", saved.getDomain());
    }
}
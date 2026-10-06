package com.vitc.dto.response;

import java.time.LocalDateTime;

/** Shown to the applicant on the Apply page once their application is saved. */
public record JobApplicationSubmittedResponse(
        String reference,
        String jobTitle,
        String companyName,
        String email,
        LocalDateTime submittedAt) {
}

package com.vitc.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Job Portal -> Apply form (job-apply.html). Sent as multipart form fields next to the resume
 * file, so it is bound with {@code @ModelAttribute}; empty optional fields arrive as "".
 */
public record JobPortalApplicationRequest(
        @NotBlank(message = "Full name is required") @Size(max = 120) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 150) String email,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone number is invalid") String phone,
        @NotBlank(message = "Current city is required") @Size(max = 120) String currentCity,
        @Past(message = "Date of birth must be in the past") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateOfBirth,
        @Size(max = 30) String gender,
        @NotBlank(message = "Highest qualification is required") @Size(max = 150) String highestQualification,
        @NotBlank(message = "College / university is required") @Size(max = 200) String institution,
        @NotNull(message = "Passing year is required")
        @Min(value = 1960, message = "Passing year is invalid") @Max(value = 2100, message = "Passing year is invalid")
        Integer graduationYear,
        @Size(max = 30) String academicScore,
        @NotNull(message = "Total experience is required")
        @Min(value = 0, message = "Experience cannot be negative") @Max(value = 60, message = "Experience is invalid")
        Integer experienceYears,
        @Size(max = 150) String currentCompany,
        @Size(max = 60) String currentCtc,
        @Size(max = 60) String expectedCtc,
        @Size(max = 60) String noticePeriod,
        @NotBlank(message = "Key skills are required") @Size(max = 1000) String skills,
        @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "LinkedIn link must start with http:// or https://")
        String linkedinUrl,
        @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "Portfolio link must start with http:// or https://")
        String portfolioUrl,
        @Size(max = 1500, message = "Cover letter can be at most 1500 characters") String coverLetter,
        @NotNull(message = "Please confirm your details are correct")
        @AssertTrue(message = "Please confirm your details are correct") Boolean consent) {
}

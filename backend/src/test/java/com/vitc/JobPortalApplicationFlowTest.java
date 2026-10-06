package com.vitc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Admin;
import com.vitc.entity.JobApplication;
import com.vitc.entity.JobRequirement;
import com.vitc.entity.User;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.AdminRepository;
import com.vitc.repository.JobApplicationRepository;
import com.vitc.repository.JobRequirementRepository;
import com.vitc.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Job Portal -> Apply -> Admin -> Job Applications: an applicant applies to a published job with
 * their details and a resume, and the Main Admin sees it against the right job and company.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobPortalApplicationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JobRequirementRepository jobRepository;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired UserRepository userRepository;

    @Value("${app.job.resume-dir:private-uploads/resumes}")
    String resumeDir;

    private static final String ADMIN_USERNAME = "jobappsadmin";
    private static final String ADMIN_TOKEN = "job-apps-admin-token";
    private static final String TEACHER_USERNAME = "JOBAPPSTEACHER";
    private static final String TEACHER_TOKEN = "job-apps-teacher-token";
    private static final byte[] PDF = "%PDF-1.7\n% test resume\n".getBytes(StandardCharsets.ISO_8859_1);

    private Long openJobId;
    private final List<String> applicantEmails = new ArrayList<>();

    @BeforeEach
    void seed() {
        openJobId = jobRepository.save(JobRequirement.builder()
                .jobTitle("Java Developer").companyName("Acme Analytics").description("Build Spring Boot services")
                .requiredSkills("Java, Spring Boot").location("Navi Mumbai").published(true).build()).getId();

        Admin admin = adminRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseGet(() -> adminRepository.save(
                Admin.builder().username(ADMIN_USERNAME).email("jobappsadmin@test.io")
                        .fullName("Job Apps Admin").passwordHash("x").build()));
        admin.setActive(true);
        admin.setSessionToken(ADMIN_TOKEN);
        admin.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        adminRepository.save(admin);

        User teacher = userRepository.findByUsernameIgnoreCase(TEACHER_USERNAME).orElseGet(() -> userRepository.save(
                User.builder().fullName("Job Apps Teacher").email("job-apps-teacher@test.io").username(TEACHER_USERNAME)
                        .passwordHash("x").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build()));
        teacher.setSessionToken(TEACHER_TOKEN);
        teacher.setSessionExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(teacher);
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (JobApplication application : applicationRepository.findAll()) {
            if (applicantEmails.stream().anyMatch(e -> e.equalsIgnoreCase(application.getEmail()))) {
                if (application.getResumeFilePath() != null) {
                    Files.deleteIfExists(resumeRoot().resolve(application.getResumeFilePath()));
                }
                applicationRepository.delete(application);
            }
        }
    }

    @Test
    void applicantApplies_andAdminSeesItForTheRightCompany_withResume() throws Exception {
        String email = "priya.applicant@example.com";
        JsonNode submitted = data(mvc.perform(application(openJobId, email, resume("Priya-CV.pdf", PDF)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(submitted.path("reference").asText()).startsWith("VITC-JA-");
        assertThat(submitted.path("companyName").asText()).isEqualTo("Acme Analytics");
        assertThat(submitted.path("jobTitle").asText()).isEqualTo("Java Developer");

        JobApplication saved = applicationRepository.findAll().stream()
                .filter(a -> email.equals(a.getEmail())).findFirst().orElseThrow();
        assertThat(saved.getJobRequirementId()).isEqualTo(openJobId);
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(saved.getDateOfBirth()).isEqualTo(LocalDate.of(2001, 4, 15));
        assertThat(saved.getCurrentCompany()).isNull(); // blank optional field stored as null
        // The resume is stored privately, never under the public uploads folder.
        Path stored = resumeRoot().resolve(saved.getResumeFilePath());
        assertThat(Files.readAllBytes(stored)).isEqualTo(PDF);
        assertThat(stored.toString()).doesNotContain(Paths.get("uploads").toAbsolutePath().toString() + java.io.File.separator);

        // Admin -> Job Applications: full profile against the right job and company.
        JsonNode row = adminRow(submitted.path("reference").asText());
        assertThat(row.path("companyName").asText()).isEqualTo("Acme Analytics");
        assertThat(row.path("jobTitle").asText()).isEqualTo("Java Developer");
        assertThat(row.path("fullName").asText()).isEqualTo("Priya Sharma");
        assertThat(row.path("graduationYear").asInt()).isEqualTo(2023);
        assertThat(row.path("skills").asText()).isEqualTo("Java, Spring Boot, SQL");
        assertThat(row.path("hasResume").asBoolean()).isTrue();
        assertThat(row.path("resumeFileName").asText()).isEqualTo("Priya-CV.pdf");

        var resume = mvc.perform(asAdmin(get("/api/v1/admin/job-applications/" + row.path("id").asLong() + "/resume")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resume.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(resume.getHeader("Content-Disposition")).contains("attachment").contains("Priya-CV.pdf");

        JsonNode updated = data(mvc.perform(asAdmin(patch("/api/v1/admin/job-applications/" + row.path("id").asLong() + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\",\"adminNotes\":\"Strong Spring Boot basics\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.path("status").asText()).isEqualTo("SHORTLISTED");
        assertThat(updated.path("adminNotes").asText()).isEqualTo("Strong Spring Boot basics");

        // One application per email per job (case-insensitive).
        mvc.perform(application(openJobId, email.toUpperCase(), resume("again.pdf", PDF))).andExpect(status().isConflict());

        mvc.perform(asAdmin(delete("/api/v1/admin/job-applications/" + row.path("id").asLong()))).andExpect(status().isOk());
        assertThat(applicationRepository.findById(row.path("id").asLong())).isEmpty();
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void invalidApplications_areRejectedWithClearMessages() throws Exception {
        String email = "rejected.applicant@example.com";

        String noResume = mvc.perform(application(openJobId, email, null))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(noResume).contains("Please attach your resume");

        mvc.perform(application(openJobId, email, resume("cv.exe", PDF))).andExpect(status().isBadRequest());
        String fake = mvc.perform(application(openJobId, email,
                        new MockMultipartFile("resume", "cv.pdf", "application/pdf", new byte[] {(byte) 0x89, 'P', 'N', 'G'})))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(fake).contains("does not look like a real PDF");

        String noConsent = mvc.perform(application(openJobId, email, resume("cv.pdf", PDF), "false"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(noConsent).contains("consent");
        mvc.perform(application(openJobId, "not-an-email", resume("cv.pdf", PDF))).andExpect(status().isBadRequest());

        Long draftJob = jobRepository.save(JobRequirement.builder().jobTitle("Draft role").companyName("Hidden Co")
                .description("d").requiredSkills("s").published(false).build()).getId();
        mvc.perform(application(draftJob, email, resume("cv.pdf", PDF))).andExpect(status().isNotFound());
        Long closedJob = jobRepository.save(JobRequirement.builder().jobTitle("Closed role").companyName("Late Co")
                .description("d").requiredSkills("s").published(true)
                .applicationDeadline(LocalDate.now().minusDays(1)).build()).getId();
        String closed = mvc.perform(application(closedJob, email, resume("cv.pdf", PDF)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(closed).contains("closed");

        assertThat(applicationRepository.findAll().stream().filter(a -> email.equals(a.getEmail()))).isEmpty();
    }

    @Test
    void adminEndpointsNeedAMainAdmin_andTheOlderCareersApiStillWorks() throws Exception {
        mvc.perform(get("/api/v1/admin/job-applications")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/admin/job-applications")
                        .header("X-Teacher-Username", TEACHER_USERNAME).header("X-Teacher-Token", TEACHER_TOKEN))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/admin/job-applications/1/resume")).andExpect(status().is4xxClientError());
        mvc.perform(asAdmin(get("/api/v1/admin/job-applications"))).andExpect(status().isOk());

        // The general careers endpoint (JSON, no job) is untouched and shows up in the admin list.
        String email = "general.careers@example.com";
        applicantEmails.add(email);
        mvc.perform(post("/api/v1/careers").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"fullName\":\"General Applicant\",\"email\":\"" + email + "\",\"phone\":\"9999999999\","
                                + "\"position\":\"Trainer\",\"experienceYears\":2}"))
                .andExpect(status().isCreated());
        JsonNode rows = data(mvc.perform(asAdmin(get("/api/v1/admin/job-applications")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean listed = false;
        for (JsonNode r : rows) {
            if (email.equals(r.path("email").asText())) {
                listed = true;
                assertThat(r.path("jobTitle").asText()).isEqualTo("Trainer");
                assertThat(r.path("hasResume").asBoolean()).isFalse();
            }
        }
        assertThat(listed).isTrue();
    }

    /* ------------------------------------------------------------------ */

    private MockMultipartHttpServletRequestBuilder application(Long jobId, String email, MockMultipartFile resume) {
        return application(jobId, email, resume, "true");
    }

    private MockMultipartHttpServletRequestBuilder application(Long jobId, String email, MockMultipartFile resume,
                                                               String consent) {
        applicantEmails.add(email);
        MockMultipartHttpServletRequestBuilder b = multipart("/api/v1/jobs/" + jobId + "/applications");
        if (resume != null) {
            b.file(resume);
        }
        b.param("fullName", "Priya Sharma").param("email", email).param("phone", "9876543210")
                .param("currentCity", "Navi Mumbai").param("dateOfBirth", "2001-04-15").param("gender", "Female")
                .param("highestQualification", "B.Sc. Computer Science").param("institution", "University of Mumbai")
                .param("graduationYear", "2023").param("academicScore", "8.4 CGPA").param("experienceYears", "1")
                .param("currentCompany", "").param("skills", "Java, Spring Boot, SQL")
                .param("linkedinUrl", "https://www.linkedin.com/in/priya").param("portfolioUrl", "")
                .param("coverLetter", "I would love to join the team.").param("consent", consent);
        return b;
    }

    private static MockMultipartFile resume(String name, byte[] bytes) {
        return new MockMultipartFile("resume", name, "application/pdf", bytes);
    }

    private JsonNode adminRow(String reference) throws Exception {
        JsonNode rows = data(mvc.perform(asAdmin(get("/api/v1/admin/job-applications")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode r : rows) {
            if (reference.equals(r.path("reference").asText())) {
                return r;
            }
        }
        throw new AssertionError("Application " + reference + " missing from Admin -> Job Applications");
    }

    private Path resumeRoot() {
        return Paths.get(resumeDir).toAbsolutePath().normalize();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder b) {
        return b.header("X-Admin-Username", ADMIN_USERNAME).header("X-Admin-Token", ADMIN_TOKEN);
    }

    private JsonNode data(String json) throws Exception {
        return mapper.readTree(json).path("data");
    }
}

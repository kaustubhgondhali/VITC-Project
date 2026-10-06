package com.vitc.service.impl;

import com.vitc.dto.request.JobApplicationStatusRequest;
import com.vitc.dto.request.JobPortalApplicationRequest;
import com.vitc.dto.response.AdminJobApplicationResponse;
import com.vitc.dto.response.JobApplicationSubmittedResponse;
import com.vitc.entity.JobApplication;
import com.vitc.entity.JobRequirement;
import com.vitc.entity.enums.ApplicationStatus;
import com.vitc.exception.BadRequestException;
import com.vitc.exception.DuplicateResourceException;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.JobApplicationRepository;
import com.vitc.repository.JobRequirementRepository;
import com.vitc.security.upload.FileContentInspector;
import com.vitc.security.upload.UploadCategory;
import com.vitc.security.upload.UploadFileValidator;
import com.vitc.service.AuditLogService;
import com.vitc.service.EmailService;
import com.vitc.service.JobPortalApplicationService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(readOnly = true)
public class JobPortalApplicationServiceImpl implements JobPortalApplicationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final JobApplicationRepository applications;
    private final JobRequirementRepository jobs;
    private final UploadFileValidator uploadValidator;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final String supportEmail;
    private final String resumeDir;

    /** Private storage root - never under the publicly served app.upload.dir. */
    private Path root;

    public JobPortalApplicationServiceImpl(JobApplicationRepository applications,
                                           JobRequirementRepository jobs,
                                           UploadFileValidator uploadValidator,
                                           EmailService emailService,
                                           AuditLogService auditLogService,
                                           @Value("${app.mail.support-email:support@vitc.local}") String supportEmail,
                                           @Value("${app.job.resume-dir:private-uploads/resumes}") String resumeDir) {
        this.applications = applications;
        this.jobs = jobs;
        this.uploadValidator = uploadValidator;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.supportEmail = supportEmail;
        this.resumeDir = resumeDir;
    }

    @PostConstruct
    void init() {
        try {
            root = Paths.get(resumeDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create resume directory: " + resumeDir, e);
        }
    }

    /* ============================ Public: apply ============================ */

    @Override
    @Transactional
    public JobApplicationSubmittedResponse apply(Long jobId, JobPortalApplicationRequest r, MultipartFile resume) {
        JobRequirement job = jobs.findById(jobId)
                .filter(j -> Boolean.TRUE.equals(j.getPublished()))
                .orElseThrow(() -> new ResourceNotFoundException("This job is no longer accepting applications."));
        if (job.getApplicationDeadline() != null && job.getApplicationDeadline().isBefore(LocalDate.now())) {
            throw new BadRequestException("Applications for this job closed on "
                    + DAY.format(job.getApplicationDeadline()) + ".");
        }
        String email = r.email().trim();
        if (applications.existsByJobRequirementIdAndEmailIgnoreCase(job.getId(), email)) {
            throw new DuplicateResourceException("You have already applied for this job with " + email
                    + ". Our team will contact you if your profile is shortlisted.");
        }
        if (resume == null || resume.isEmpty()) {
            throw new BadRequestException("Please attach your resume (" + UploadCategory.RESUME.allowedHint() + ").");
        }
        UploadFileValidator.ValidatedUpload validated = uploadValidator.validate(resume, UploadCategory.RESUME);
        // The extension and declared type are the applicant's to choose; the bytes are not.
        if (!FileContentInspector.matchesExtension(validated.extension(), head(resume))) {
            throw new BadRequestException("Your resume does not look like a real "
                    + validated.extension().toUpperCase(Locale.ROOT) + " file. Please upload a PDF or Word document.");
        }
        store(resume, validated.storedName());
        // A rolled-back application must not leave its resume behind on disk.
        afterCompletion(committed -> {
            if (!committed) {
                deleteQuietly(validated.storedName());
            }
        });

        JobApplication saved = applications.save(JobApplication.builder()
                .jobRequirementId(job.getId())
                .position(limit(job.getJobTitle(), 120))
                .companyName(job.getCompanyName())
                .fullName(r.fullName().trim())
                .email(email)
                .phone(r.phone().trim())
                .currentCity(clean(r.currentCity()))
                .dateOfBirth(r.dateOfBirth())
                .gender(clean(r.gender()))
                .highestQualification(clean(r.highestQualification()))
                .institution(clean(r.institution()))
                .graduationYear(r.graduationYear())
                .academicScore(clean(r.academicScore()))
                .experienceYears(r.experienceYears())
                .currentCompany(clean(r.currentCompany()))
                .currentCtc(clean(r.currentCtc()))
                .expectedCtc(clean(r.expectedCtc()))
                .noticePeriod(clean(r.noticePeriod()))
                .skills(clean(r.skills()))
                .linkedinUrl(clean(r.linkedinUrl()))
                .portfolioUrl(clean(r.portfolioUrl()))
                .message(clean(r.coverLetter()))
                .resumeFilePath(validated.storedName())
                .resumeFileName(validated.originalName())
                .resumeContentType(validated.contentType())
                .resumeSizeBytes(validated.sizeBytes())
                .status(ApplicationStatus.SUBMITTED)
                .build());

        String reference = reference(saved.getId());
        String subject = "We received your application - " + saved.getPosition();
        String html = StudentEmailTemplates.jobApplicationReceived(saved.getFullName(), saved.getPosition(),
                saved.getCompanyName(), reference, supportEmail);
        // Acknowledged only once the application is safely stored; a mail problem never undoes it.
        afterCommit(() -> {
            try {
                emailService.sendHtml(email, subject, html);
            } catch (Exception ex) {
                log.warn("Acknowledgement for job application {} could not be emailed: {}", reference, ex.getMessage());
            }
        });
        log.info("Job application {} received for job {} at {}", reference, job.getId(), job.getCompanyName());
        return new JobApplicationSubmittedResponse(reference, saved.getPosition(), saved.getCompanyName(), email,
                saved.getCreatedAt());
    }

    /* ============================ Admin ============================ */

    @Override
    public List<AdminJobApplicationResponse> list() {
        return applications.findAllByOrderByCreatedAtDesc().stream().map(this::toAdminResponse).toList();
    }

    @Override
    @Transactional
    public AdminJobApplicationResponse updateStatus(Long id, JobApplicationStatusRequest request) {
        JobApplication application = find(id);
        ApplicationStatus previous = application.getStatus();
        application.setStatus(request.status());
        application.setAdminNotes(clean(request.adminNotes()));
        JobApplication saved = applications.save(application);
        auditLogService.log("JOB_APPLICATION_STATUS_CHANGED", "JobApplication", String.valueOf(id),
                reference(id) + ": " + previous + " -> " + request.status());
        return toAdminResponse(saved);
    }

    @Override
    @Transactional
    public ResumeDownload openResume(Long id) {
        JobApplication application = find(id);
        if (application.getResumeFilePath() == null) {
            throw new ResourceNotFoundException("This application has no uploaded resume.");
        }
        Path path = root.resolve(application.getResumeFilePath()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            log.error("Resume of job application {} is missing on disk ({})", id, application.getResumeFilePath());
            throw new ResourceNotFoundException("The resume file for this application is missing.");
        }
        // Personal documents: keep a trail of who opened them.
        auditLogService.log("JOB_APPLICATION_RESUME_DOWNLOADED", "JobApplication", String.valueOf(id),
                "Downloaded the resume of " + reference(id));
        String contentType = application.getResumeContentType() == null || application.getResumeContentType().isBlank()
                ? "application/octet-stream" : application.getResumeContentType();
        return new ResumeDownload(new FileSystemResource(path), application.getResumeFileName(), contentType,
                path.toFile().length());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        JobApplication application = find(id);
        String resumeFile = application.getResumeFilePath();
        applications.delete(application);
        afterCompletion(committed -> {
            if (committed) {
                deleteQuietly(resumeFile);
            }
        });
        auditLogService.log("JOB_APPLICATION_DELETED", "JobApplication", String.valueOf(id),
                "Deleted job application " + reference(id));
    }

    /* ============================ Helpers ============================ */

    private JobApplication find(Long id) {
        return applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("JobApplication", id));
    }

    private AdminJobApplicationResponse toAdminResponse(JobApplication a) {
        return new AdminJobApplicationResponse(a.getId(), reference(a.getId()), a.getJobRequirementId(),
                a.getPosition(), a.getCompanyName(), a.getFullName(), a.getEmail(), a.getPhone(), a.getCurrentCity(),
                a.getDateOfBirth(), a.getGender(), a.getHighestQualification(), a.getInstitution(),
                a.getGraduationYear(), a.getAcademicScore(), a.getExperienceYears(), a.getCurrentCompany(),
                a.getCurrentCtc(), a.getExpectedCtc(), a.getNoticePeriod(), a.getSkills(), a.getLinkedinUrl(),
                a.getPortfolioUrl(), a.getMessage(), a.getResumeFilePath() != null, a.getResumeFileName(),
                a.getResumeSizeBytes(), a.getResumeUrl(), a.getStatus(), a.getAdminNotes(), a.getCreatedAt());
    }

    /** Human-friendly application number shown to the applicant and in the admin panel. */
    private static String reference(Long id) {
        return String.format(Locale.ENGLISH, "VITC-JA-%05d", id);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileContentInspector.SNIFF_BYTES);
        } catch (IOException e) {
            throw new BadRequestException("Your resume could not be read. Please try again.");
        }
    }

    private void store(MultipartFile file, String storedName) {
        Path destination = root.resolve(storedName).normalize();
        if (!destination.startsWith(root)) {
            throw new BadRequestException("Invalid file name");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Could not store resume '{}': {}", storedName, e.getMessage(), e);
            throw new BadRequestException("Your resume could not be saved. Please try again.");
        }
    }

    private void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        try {
            Path path = root.resolve(storedName).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not remove resume file '{}': {}", storedName, e.getMessage());
        }
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

    private static void afterCompletion(Consumer<Boolean> action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }
}

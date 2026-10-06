package com.vitc.service.impl;

import com.vitc.dto.request.*;
import com.vitc.dto.response.*;
import com.vitc.entity.*;
import com.vitc.exception.ResourceNotFoundException;
import com.vitc.repository.*;
import com.vitc.service.JobPortalService;
import com.vitc.service.OwnerNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional
public class JobPortalServiceImpl implements JobPortalService {
    private final JobRequirementRepository jobs;
    private final EmployerEnquiryRepository enquiries;
    private final OwnerNotificationService ownerNotifications;

    @Override @Transactional(readOnly = true)
    public List<JobRequirementResponse> publicJobs() { return jobs.findByPublishedTrueOrderByCreatedAtDesc().stream().map(JobRequirementResponse::of).toList(); }
    @Override @Transactional(readOnly = true)
    public List<JobRequirementResponse> allJobs() { return jobs.findAll().stream().map(JobRequirementResponse::of).toList(); }

    @Override public JobRequirementResponse save(Long id, JobRequirementRequest r) {
        JobRequirement j = id == null ? new JobRequirement() : jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job requirement not found"));
        j.setJobTitle(r.jobTitle()); j.setCompanyName(r.companyName()); j.setDescription(r.description()); j.setRequiredSkills(r.requiredSkills());
        j.setExperience(r.experience()); j.setQualification(r.qualification()); j.setLocation(r.location()); j.setEmploymentType(r.employmentType());
        j.setSalaryCtc(r.salaryCtc()); j.setOpenings(r.openings()); j.setApplicationDeadline(r.applicationDeadline()); j.setApplicationMethod(r.applicationMethod());
        if (r.published() != null) j.setPublished(r.published());
        return JobRequirementResponse.of(jobs.save(j));
    }
    @Override public void deleteJob(Long id) { jobs.deleteById(id); }

    @Override public EmployerEnquiryResponse submit(EmployerEnquiryRequest r) {
        EmployerEnquiry e = EmployerEnquiry.builder().companyName(r.companyName()).contactPerson(r.contactPerson()).email(r.email())
                .phone(r.phone()).jobTitle(r.jobTitle()).openings(r.openings()).requiredSkills(r.requiredSkills())
                .experienceRequired(r.experienceRequired()).location(r.location()).message(r.message()).build();
        EmployerEnquiry saved = enquiries.save(e);
        // Owner is emailed after commit; WhatsApp is the visitor's click-to-chat in the browser.
        ownerNotifications.employerEnquiryReceived(saved);
        return EmployerEnquiryResponse.of(saved);
    }
    @Override @Transactional(readOnly = true)
    public List<EmployerEnquiryResponse> enquiries() { return enquiries.findAllByOrderByCreatedAtDesc().stream().map(EmployerEnquiryResponse::of).toList(); }
    @Override public EmployerEnquiryResponse updateEnquiry(Long id, EnquiryStatusRequest r) {
        EmployerEnquiry e = enquiries.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employer enquiry not found"));
        e.setStatus(r.status()); return EmployerEnquiryResponse.of(enquiries.save(e));
    }
    @Override public void deleteEnquiry(Long id) { enquiries.deleteById(id); }
}

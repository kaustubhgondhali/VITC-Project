package com.vitc.repository;

import com.vitc.entity.JobApplication;
import com.vitc.entity.enums.ApplicationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByStatus(ApplicationStatus status);

    List<JobApplication> findByPositionIgnoreCase(String position);

    List<JobApplication> findAllByOrderByCreatedAtDesc();

    boolean existsByJobRequirementIdAndEmailIgnoreCase(Long jobRequirementId, String email);
}

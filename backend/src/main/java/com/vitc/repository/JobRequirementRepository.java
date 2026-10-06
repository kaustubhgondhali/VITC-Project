package com.vitc.repository;

import com.vitc.entity.JobRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, Long> {
    List<JobRequirement> findByPublishedTrueOrderByCreatedAtDesc();
    long countByPublishedTrue();
}

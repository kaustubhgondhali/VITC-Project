package com.vitc.repository;

import com.vitc.entity.InternshipApplication;
import com.vitc.entity.enums.ApplicationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InternshipApplicationRepository extends JpaRepository<InternshipApplication, Long> {

    List<InternshipApplication> findByStatus(ApplicationStatus status);

    boolean existsByEmailAndDomain(String email, String domain);
}

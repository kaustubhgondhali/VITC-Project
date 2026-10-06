package com.vitc.repository;

import com.vitc.entity.EmployerEnquiry;
import com.vitc.entity.enums.EnquiryStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerEnquiryRepository extends JpaRepository<EmployerEnquiry, Long> {
    List<EmployerEnquiry> findAllByOrderByCreatedAtDesc();
    long countByStatus(EnquiryStatus status);
}

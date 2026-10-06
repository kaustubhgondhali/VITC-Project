package com.vitc.repository;

import com.vitc.entity.EmailDeliveryLog;
import com.vitc.entity.enums.EmailDeliveryStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailDeliveryLogRepository extends JpaRepository<EmailDeliveryLog, Long> {

    Optional<EmailDeliveryLog> findByDedupeKey(String dedupeKey);

    List<EmailDeliveryLog> findTop200ByOrderByIdDesc();

    List<EmailDeliveryLog> findByStatusOrderByIdDesc(EmailDeliveryStatus status);

    List<EmailDeliveryLog> findByStudentUserIdOrderByIdDesc(Long studentUserId);
}

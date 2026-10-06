package com.vitc.repository;

import com.vitc.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PART 2C-1/7 - persistence for the centralized audit log. Read methods are
 * provided now as part of the foundation; an admin-facing listing endpoint
 * (with pagination/filtering) is a later part, not implemented here.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByIdDesc();

    List<AuditLog> findTop200ByUserIdOrderByIdDesc(Long userId);

    List<AuditLog> findTop200ByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId);

    List<AuditLog> findTop200ByActionOrderByIdDesc(String action);
}

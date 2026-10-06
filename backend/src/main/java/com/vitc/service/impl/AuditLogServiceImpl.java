package com.vitc.service.impl;

import com.vitc.entity.AuditLog;
import com.vitc.repository.AuditLogRepository;
import com.vitc.security.CurrentUser;
import com.vitc.security.CurrentUserContext;
import com.vitc.security.RequestMetadataUtil;
import com.vitc.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PART 2C-1/7 - centralized audit-event recorder. See {@link AuditLogService}
 * for the contract; this class owns the two things every caller must not do
 * itself: resolving who is acting (from {@link CurrentUserContext} and the
 * current request only - never from caller input) and sanitizing the
 * description before it is ever persisted.
 *
 * <p>Recording an audit event never breaks the calling operation: any
 * failure while persisting is logged and swallowed rather than propagated,
 * since a missed audit row must never fail a login, payment, or admin
 * action.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /**
     * Redacts anything that looks like a secret being carried inline in a
     * description, e.g. "reset using token abc123" or "password=Secret1".
     * This is a defense-in-depth net - callers must still never intentionally
     * pass a secret in {@code description}.
     */
    private static final Pattern SECRET_LIKE_PATTERN = Pattern.compile(
            "(?i)(token|password|secret|api[_-]?key|authorization|jwt)\\s*[:=]?\\s*\\S+");

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void log(String action, String entityType, String entityId, String description) {
        record(action, entityType, entityId, description);
    }

    @Override
    @Transactional
    public void log(String action, String description) {
        record(action, null, null, description);
    }

    private void record(String action, String entityType, String entityId, String description) {
        try {
            CurrentUser currentUser = CurrentUserContext.get();
            HttpServletRequest request = RequestMetadataUtil.currentRequest();

            AuditLog entry = AuditLog.builder()
                    .userId(currentUser != null ? currentUser.id() : null)
                    .actorIdentifier(currentUser != null ? currentUser.identifier() : null)
                    .role(currentUser != null ? currentUser.role().name() : null)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(sanitize(description))
                    .ipAddress(RequestMetadataUtil.resolveClientIp(request))
                    .userAgent(RequestMetadataUtil.resolveUserAgent(request))
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // An audit-logging failure must never break the operation being audited.
            log.warn("Failed to record audit log for action={} entityType={} entityId={}: {}",
                    action, entityType, entityId, ex.getMessage());
        }
    }

    /** Truncates and redacts anything that looks like a secret before it is persisted. */
    private String sanitize(String description) {
        if (description == null) {
            return null;
        }
        String redacted = SECRET_LIKE_PATTERN.matcher(description).replaceAll("$1=[REDACTED]");
        return redacted.length() > MAX_DESCRIPTION_LENGTH
                ? redacted.substring(0, MAX_DESCRIPTION_LENGTH)
                : redacted;
    }
}

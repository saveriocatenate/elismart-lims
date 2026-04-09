package it.elismart_lims.service.audit;

import it.elismart_lims.dto.AuditLogResponse;
import it.elismart_lims.mapper.AuditLogMapper;
import it.elismart_lims.model.AuditLog;
import it.elismart_lims.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Single entry point for writing to the {@code audit_log} table.
 *
 * <p>All field-level changes across every auditable entity must go through this
 * service. Direct repository writes are prohibited by convention — see CLAUDE.md,
 * "Change history" section.</p>
 *
 * <p>The {@code changedBy} value is always resolved from the Spring Security
 * {@link SecurityContextHolder}; callers must never supply it.</p>
 */
@Service
@Transactional
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String SYSTEM = "system";

    private final AuditLogRepository auditLogRepository;

    /**
     * Constructs an {@code AuditLogService} with the required repository.
     *
     * @param auditLogRepository the repository used to persist audit entries
     */
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records a single field-level change.
     *
     * <p>{@code changedBy} is resolved from the current security context;
     * {@code changedAt} is set to {@link LocalDateTime#now()}.</p>
     *
     * @param entityType simple class name of the modified entity (e.g. {@code "Experiment"})
     * @param entityId   primary key of the modified entity row
     * @param fieldName  Java field name that changed (e.g. {@code "status"})
     * @param oldValue   string representation of the previous value; {@code null} for creation
     * @param newValue   string representation of the new value; {@code null} for deletion
     * @param reason     free-text justification; mandatory for status overrides, optional otherwise
     */
    public void logChange(String entityType, Long entityId,
                          String fieldName, String oldValue, String newValue,
                          String reason) {
        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(resolveCurrentUser())
                .changedAt(LocalDateTime.now())
                .reason(reason)
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit: {} id={} field={} [{} → {}] by={}",
                entityType, entityId, fieldName, oldValue, newValue, entry.getChangedBy());
    }

    /**
     * Records multiple field-level changes for the same entity in a single call.
     *
     * <p>Each entry in {@code changes} maps a field name to a {@link Pair} of
     * {@code (oldValue, newValue)}. A separate {@link AuditLog} row is persisted
     * for every entry. All entries share the same {@code changedBy}, {@code changedAt},
     * and {@code reason}.</p>
     *
     * @param entityType simple class name of the modified entity
     * @param entityId   primary key of the modified entity row
     * @param changes    map of {@code fieldName → Pair(oldValue, newValue)}
     * @param reason     shared justification for all changes in this batch; may be {@code null}
     */
    public void logChanges(String entityType, Long entityId,
                           Map<String, Pair<String, String>> changes,
                           String reason) {
        if (changes == null || changes.isEmpty()) {
            return;
        }

        String changedBy = resolveCurrentUser();
        LocalDateTime changedAt = LocalDateTime.now();

        List<AuditLog> entries = changes.entrySet().stream()
                .map(e -> AuditLog.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .fieldName(e.getKey())
                        .oldValue(e.getValue().getFirst())
                        .newValue(e.getValue().getSecond())
                        .changedBy(changedBy)
                        .changedAt(changedAt)
                        .reason(reason)
                        .build())
                .toList();

        auditLogRepository.saveAll(entries);
        log.debug("Audit: {} id={} — {} field(s) logged by={}", entityType, entityId, entries.size(), changedBy);
    }

    /**
     * Returns the full change history for a specific entity row, newest first.
     *
     * @param entityType simple class name of the entity (e.g. {@code "Experiment"})
     * @param entityId   primary key of the entity row
     * @return list of audit entries ordered by {@code changedAt} descending
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getHistory(String entityType, Long entityId) {
        List<AuditLog> entries = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByChangedAtDesc(entityType, entityId);
        return AuditLogMapper.toResponseList(entries);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the username of the currently authenticated principal.
     * Falls back to {@code "system"} for unauthenticated or anonymous requests.
     *
     * @return the current username, never {@code null}
     */
    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return SYSTEM;
        }
        return auth.getName();
    }
}

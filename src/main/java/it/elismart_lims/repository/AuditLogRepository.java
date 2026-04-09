package it.elismart_lims.repository;

import it.elismart_lims.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link AuditLog} entries.
 *
 * <p>This repository is read/write at the persistence level, but by convention
 * only {@link it.elismart_lims.service.audit.AuditLogService} may call {@code save()}.
 * No other service or controller should write to this table directly.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Returns all audit entries for a specific entity, newest first.
     *
     * @param entityType simple class name of the entity (e.g. {@code "Experiment"})
     * @param entityId   primary key of the entity row
     * @return list of matching entries ordered by {@code changedAt} descending
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByChangedAtDesc(String entityType, Long entityId);
}

package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable record of a single field-level change to any auditable entity.
 *
 * <p>This entity does NOT extend {@link Auditable}: it is itself the audit trail
 * and must never be modified or deleted. All writes go through
 * {@link it.elismart_lims.service.audit.AuditLogService#logChange}.</p>
 *
 * <p>The table is append-only: no UPDATE or DELETE operations are allowed on it.</p>
 */
@Entity
@Table(
        name = "audit_log",
        indexes = @Index(name = "idx_audit_log_entity", columnList = "entity_type, entity_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Simple class name of the modified entity (e.g. {@code "Experiment"},
     * {@code "MeasurementPair"}).
     */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** Primary key of the modified entity row. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Java field name that was changed (e.g. {@code "status"}, {@code "signal1"}).
     * Use the entity field name, not the DB column name.
     */
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /**
     * String representation of the value before the change.
     * {@code null} for creation events (no previous value exists).
     */
    @Column(name = "old_value", length = 4000)
    private String oldValue;

    /**
     * String representation of the value after the change.
     * {@code null} for deletion events.
     */
    @Column(name = "new_value", length = 4000)
    private String newValue;

    /**
     * Username of the principal who made the change.
     * Populated from {@link org.springframework.security.core.context.SecurityContextHolder}.
     */
    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    /**
     * Timestamp when the change was recorded.
     * Set to {@link LocalDateTime#now()} at service layer — never supplied by the caller.
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * Free-text justification for the change.
     * Optional in general; mandatory for status overrides (e.g. KO → OK).
     */
    @Column(name = "reason", length = 1000)
    private String reason;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog that = (AuditLog) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

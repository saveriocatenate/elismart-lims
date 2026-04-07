package it.elismart_lims.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base class that adds automatic audit columns to every entity that extends it.
 *
 * <p>{@code createdAt} and {@code createdBy} are set once on insert and never
 * updated. {@code updatedAt} is refreshed on every update. The actual values
 * are supplied by the {@code AuditorAware<String>} bean registered in
 * {@link it.elismart_lims.config.JpaAuditingConfig}; once real authentication
 * is in place, replace the {@code AuditorAwareImpl} implementation with one
 * that reads the authenticated principal instead of returning {@code "system"}.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class Auditable {

    /**
     * Timestamp of the first INSERT for this record.
     * Set automatically by Spring Data JPA auditing; never written by application code.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent UPDATE for this record.
     * Null for records that have never been modified after initial creation.
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Identity of the principal who created this record.
     * Currently, always {@code "system"} because the backend has no per-user authentication.
     * Update {@link it.elismart_lims.config.AuditorAwareImpl} when auth is added.
     */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;
}

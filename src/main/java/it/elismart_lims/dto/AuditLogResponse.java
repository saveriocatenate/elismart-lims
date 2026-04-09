package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response payload for a single {@link it.elismart_lims.model.AuditLog} entry.
 */
@Builder
public record AuditLogResponse(
        Long id,
        String entityType,
        Long entityId,
        String fieldName,
        String oldValue,
        String newValue,
        String changedBy,
        LocalDateTime changedAt,
        String reason
) {
}

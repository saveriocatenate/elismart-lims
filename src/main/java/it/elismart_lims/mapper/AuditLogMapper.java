package it.elismart_lims.mapper;

import it.elismart_lims.dto.AuditLogResponse;
import it.elismart_lims.model.AuditLog;

import java.util.List;

/**
 * Static mapper between {@link AuditLog} entities and their DTOs.
 *
 * <p>This class is a static utility: it is {@code final}, all methods are {@code static},
 * and the private constructor prevents instantiation. Never inject this class as a bean.</p>
 */
public final class AuditLogMapper {

    private AuditLogMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a single {@link AuditLog} entity to an {@link AuditLogResponse} DTO.
     *
     * @param log the audit log entry
     * @return the corresponding response DTO
     */
    public static AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .fieldName(log.getFieldName())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .changedBy(log.getChangedBy())
                .changedAt(log.getChangedAt())
                .reason(log.getReason())
                .build();
    }

    /**
     * Converts a list of {@link AuditLog} entities to a list of {@link AuditLogResponse} DTOs.
     * Order is preserved.
     *
     * @param logs the list of audit log entries
     * @return the corresponding list of response DTOs
     */
    public static List<AuditLogResponse> toResponseList(List<AuditLog> logs) {
        return logs.stream().map(AuditLogMapper::toResponse).toList();
    }
}

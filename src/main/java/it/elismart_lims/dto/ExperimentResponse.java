package it.elismart_lims.dto;

import it.elismart_lims.model.ExperimentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for Experiment entities with nested collections and audit metadata.
 */
@Builder
public record ExperimentResponse(
        Long id,
        String name,
        LocalDateTime date,
        ExperimentStatus status,
        String protocolName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        List<UsedReagentBatchResponse> usedReagentBatches,
        List<MeasurementPairResponse> measurementPairs
) {
}

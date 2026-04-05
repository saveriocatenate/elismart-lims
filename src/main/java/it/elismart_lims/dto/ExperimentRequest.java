package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for creating an Experiment. Includes mandatory reagent batch IDs and measurement pairs.
 */
public record ExperimentRequest(
        @NotBlank String name,
        @NotNull LocalDateTime date,
        @NotNull Long protocolId,
        @NotNull String status,
        @NotNull List<Long> usedReagentBatchIds,
        @NotNull List<MeasurementPairRequest> measurementPairs
) {
}

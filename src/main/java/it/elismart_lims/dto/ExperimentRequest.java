package it.elismart_lims.dto;

import it.elismart_lims.model.ExperimentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for creating an Experiment.
 * Includes inline reagent batch data and measurement pairs.
 */
public record ExperimentRequest(
        @NotBlank String name,
        @NotNull LocalDateTime date,
        @NotNull Long protocolId,
        @NotNull ExperimentStatus status,
        @NotNull List<UsedReagentBatchRequest> usedReagentBatches,
        @NotNull List<MeasurementPairRequest> measurementPairs
) {
}

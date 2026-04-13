package it.elismart_lims.dto;

import it.elismart_lims.model.ExperimentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for updating an existing Experiment.
 *
 * <p>The protocol and which reagents are used cannot be changed after creation.
 * Only {@code name}, {@code date}, {@code status}, individual reagent batch details, and
 * raw measurement pair signals are mutable. The total number of measurement pairs is fixed.</p>
 */
public record ExperimentUpdateRequest(
        /* New human-readable label for this experiment run. */
        @NotBlank String name,
        /* New date and time the experiment was performed. */
        @NotNull LocalDateTime date,
        /* New lifecycle / validation status. */
        @NotNull ExperimentStatus status,
        /* Per-batch updates; may be empty if no batch details change. */
        @NotNull List<UsedReagentBatchUpdateRequest> reagentBatchUpdates,
        /*
         * Per-pair signal updates; may be {@code null} or empty if no pair values change.
         * Signal mean, %CV, and %Recovery are recalculated server-side.
         */
        List<MeasurementPairUpdateRequest> measurementPairUpdates
) {
}

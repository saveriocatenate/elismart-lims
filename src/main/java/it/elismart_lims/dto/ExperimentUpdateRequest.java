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
 *
 * <p>When the status transition involves a terminal state ({@code OK} or {@code KO}),
 * the {@code reason} field is mandatory and must not be blank. This enforces an electronic
 * justification for any manual override of a validated result, as required by
 * 21 CFR Part 11 §11.10(e) and OECD GLP §10.3.</p>
 */
public record ExperimentUpdateRequest(
        /** New human-readable label for this experiment run. */
        @NotBlank String name,
        /** New date and time the experiment was performed. */
        @NotNull LocalDateTime date,
        /** New lifecycle / validation status. */
        @NotNull ExperimentStatus status,
        /** Per-batch updates; may be empty if no batch details change. */
        @NotNull List<UsedReagentBatchUpdateRequest> reagentBatchUpdates,
        /**
         * Per-pair signal updates; may be {@code null} or empty if no pair values change.
         * Signal mean, %CV, and %Recovery are recalculated server-side.
         */
        List<MeasurementPairUpdateRequest> measurementPairUpdates,
        /**
         * Free-text justification for the change.
         * <strong>Required</strong> when the status transition involves a terminal state
         * ({@code OK} or {@code KO}). Optional for other field updates.
         */
        String reason
) {
}

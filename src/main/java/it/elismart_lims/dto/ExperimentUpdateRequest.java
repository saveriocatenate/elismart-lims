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
 * Only {@code name}, {@code date}, {@code status}, and individual reagent batch
 * details ({@code lotNumber}, {@code expiryDate}) are mutable.</p>
 */
public record ExperimentUpdateRequest(
        /** New human-readable label for this experiment run. */
        @NotBlank String name,
        /** New date and time the experiment was performed. */
        @NotNull LocalDateTime date,
        /** New lifecycle / validation status. */
        @NotNull ExperimentStatus status,
        /** Per-batch updates; may be empty if no batch details change. */
        @NotNull List<UsedReagentBatchUpdateRequest> reagentBatchUpdates
) {
}

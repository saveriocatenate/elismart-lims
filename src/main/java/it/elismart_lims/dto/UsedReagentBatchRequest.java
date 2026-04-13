package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for linking a pre-registered {@link it.elismart_lims.model.ReagentBatch}
 * to an experiment.
 *
 * @param reagentBatchId ID of the {@link it.elismart_lims.model.ReagentBatch} to link (required)
 */
public record UsedReagentBatchRequest(
        @NotNull Long reagentBatchId
) {
}

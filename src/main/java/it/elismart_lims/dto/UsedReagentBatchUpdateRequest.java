package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for re-linking a {@link it.elismart_lims.model.UsedReagentBatch}
 * to a different {@link it.elismart_lims.model.ReagentBatch}.
 *
 * <p>Only the {@code reagentBatchId} is mutable after creation; the linked experiment
 * cannot be changed.</p>
 *
 * @param id             database ID of the {@link it.elismart_lims.model.UsedReagentBatch} to update
 * @param reagentBatchId new {@link it.elismart_lims.model.ReagentBatch} to link
 */
public record UsedReagentBatchUpdateRequest(
        /** Database ID of the UsedReagentBatch record to update. */
        @NotNull Long id,
        /** ID of the replacement ReagentBatch. */
        @NotNull Long reagentBatchId
) {
}

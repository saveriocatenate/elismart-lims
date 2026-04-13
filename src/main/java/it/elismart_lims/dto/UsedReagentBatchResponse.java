package it.elismart_lims.dto;

import lombok.Builder;

/**
 * Response payload for {@link it.elismart_lims.model.UsedReagentBatch} entities.
 *
 * @param id           primary key of the UsedReagentBatch record
 * @param reagentBatch the linked reagent batch, including lot number, expiry date, reagent name
 *                     and manufacturer
 */
@Builder
public record UsedReagentBatchResponse(
        Long id,
        ReagentBatchResponse reagentBatch
) {
}

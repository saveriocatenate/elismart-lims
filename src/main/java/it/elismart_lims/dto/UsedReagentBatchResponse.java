package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Response payload for UsedReagentBatch entities.
 */
@Builder
public record UsedReagentBatchResponse(
        Long id,
        String reagentName,
        String lotNumber,
        LocalDate expiryDate
) {
}

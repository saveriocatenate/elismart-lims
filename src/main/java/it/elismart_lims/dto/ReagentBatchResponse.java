package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Response payload for {@link it.elismart_lims.model.ReagentBatch} entities.
 *
 * @param id           primary key
 * @param reagentId    ID of the parent reagent catalog entry
 * @param reagentName  commercial name of the parent reagent
 * @param manufacturer manufacturer of the parent reagent
 * @param lotNumber    manufacturer's lot number
 * @param expiryDate   expiry date of this batch
 * @param supplier     optional supplier or distributor
 * @param notes        optional free-text notes
 */
@Builder
public record ReagentBatchResponse(
        Long id,
        Long reagentId,
        String reagentName,
        String manufacturer,
        String lotNumber,
        LocalDate expiryDate,
        String supplier,
        String notes
) {
}

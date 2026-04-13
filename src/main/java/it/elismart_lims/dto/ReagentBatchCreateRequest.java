package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for registering a new {@link it.elismart_lims.model.ReagentBatch}.
 *
 * @param reagentId  ID of the reagent catalog entry this batch belongs to (required)
 * @param lotNumber  manufacturer's lot number (required)
 * @param expiryDate expiry date printed on the batch label (required)
 * @param supplier   optional supplier or distributor for this batch
 * @param notes      optional free-text notes
 */
public record ReagentBatchCreateRequest(
        @NotNull Long reagentId,
        @NotBlank String lotNumber,
        @NotNull LocalDate expiryDate,
        String supplier,
        String notes
) {
}

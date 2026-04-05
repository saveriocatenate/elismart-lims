package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for creating a UsedReagentBatch.
 */
public record UsedReagentBatchRequest(
        @NotNull Long reagentId,
        @NotBlank String lotNumber,
        LocalDate expiryDate
) {
}

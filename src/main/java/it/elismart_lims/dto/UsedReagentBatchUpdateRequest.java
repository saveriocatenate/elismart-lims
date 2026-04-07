package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request payload for updating a single {@link it.elismart_lims.model.UsedReagentBatch}.
 *
 * <p>Identified by its database {@code id}. Only {@code lotNumber} and {@code expiryDate}
 * are mutable after creation; the linked reagent and experiment cannot be changed.</p>
 */
public record UsedReagentBatchUpdateRequest(
        /** Database ID of the batch to update. */
        @NotNull Long id,
        /** New manufacturer lot / batch number. */
        @NotBlank String lotNumber,
        /** New expiry date; {@code null} if not available on the label. */
        LocalDate expiryDate
) {
}

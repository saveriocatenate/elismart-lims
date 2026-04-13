package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Alert payload for a {@link it.elismart_lims.model.ReagentBatch} that is expiring soon.
 *
 * @param reagentId      ID of the parent reagent catalog entry
 * @param reagentName    commercial name of the reagent
 * @param manufacturer   reagent manufacturer
 * @param lotNumber      manufacturer's lot number
 * @param expiryDate     date the batch expires
 * @param daysUntilExpiry number of days from today until expiry (0 = expires today)
 */
@Builder
public record ExpiringReagentAlert(
        Long reagentId,
        String reagentName,
        String manufacturer,
        String lotNumber,
        LocalDate expiryDate,
        int daysUntilExpiry
) {
}

package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for the PATCH /api/measurement-pairs/{id}/outlier endpoint.
 */
public record OutlierUpdateRequest(
        @NotNull(message = "isOutlier must not be null")
        Boolean isOutlier
) {
}

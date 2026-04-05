package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a MeasurementPair.
 */
public record MeasurementPairRequest(
        @NotBlank String pairType,
        Double concentrationNominal,
        Double signal1,
        Double signal2,
        Double signalMean,
        Double cvPct,
        Double recoveryPct,
        Boolean isOutlier
) {
}

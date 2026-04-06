package it.elismart_lims.dto;

import it.elismart_lims.model.PairType;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating a MeasurementPair.
 */
public record MeasurementPairRequest(
        @NotNull PairType pairType,
        Double concentrationNominal,
        Double signal1,
        Double signal2,
        Double signalMean,
        Double cvPct,
        Double recoveryPct,
        Boolean isOutlier
) {
}

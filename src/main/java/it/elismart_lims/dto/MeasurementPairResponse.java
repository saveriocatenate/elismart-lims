package it.elismart_lims.dto;

import it.elismart_lims.model.PairType;
import lombok.Builder;

/**
 * Response payload for MeasurementPair entities.
 */
@Builder
public record MeasurementPairResponse(
        Long id,
        PairType pairType,
        Double concentrationNominal,
        Double signal1,
        Double signal2,
        Double signalMean,
        Double cvPct,
        Double recoveryPct,
        Boolean isOutlier
) {
}

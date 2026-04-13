package it.elismart_lims.dto;

import it.elismart_lims.model.PairType;
import lombok.Builder;

/**
 * Response payload for MeasurementPair entities.
 *
 * <p>{@code sample} is non-null only for pairs of type {@link PairType#SAMPLE} that
 * have an associated {@link it.elismart_lims.model.Sample} entity.</p>
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
        Boolean isOutlier,
        SampleResponse sample
) {
}

package it.elismart_lims.dto;

import it.elismart_lims.model.PairStatus;
import it.elismart_lims.model.PairType;
import lombok.Builder;

/**
 * Response payload for MeasurementPair entities.
 *
 * <p>{@code sample} is non-null only for pairs of type {@link PairType#SAMPLE} that
 * have an associated {@link it.elismart_lims.model.Sample} entity.</p>
 *
 * <p>{@code pairStatus} is computed server-side by
 * {@link it.elismart_lims.mapper.MeasurementPairMapper} when protocol limits and experiment
 * status are available. It is {@code null} when the pair is returned outside an experiment
 * context (e.g., a PATCH /outlier response).</p>
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
        SampleResponse sample,
        PairStatus pairStatus
) {
}

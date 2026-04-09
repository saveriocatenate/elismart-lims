package it.elismart_lims.dto;

import it.elismart_lims.model.PairType;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating a MeasurementPair.
 *
 * <p>{@code signalMean} and {@code cvPct} are derived server-side from
 * {@code signal1} and {@code signal2} and are therefore absent from this request.
 * Any client-supplied value for those fields would be ignored — and with
 * {@code spring.jackson.deserialization.fail-on-unknown-properties=true} it would
 * cause a 400 Bad Request instead.</p>
 */
public record MeasurementPairRequest(
        @NotNull PairType pairType,
        Double concentrationNominal,
        @NotNull Double signal1,
        @NotNull Double signal2,
        Double recoveryPct,
        Boolean isOutlier
) {
}

package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating the raw signal values of an existing MeasurementPair.
 *
 * <p>Signal mean, %CV, and %Recovery are recalculated server-side from the new signal
 * values and nominal concentration, so they must not be supplied by the caller.</p>
 */
public record MeasurementPairUpdateRequest(
        /** The ID of the MeasurementPair to update. */
        @NotNull Long id,

        /** New first replicate signal reading. */
        @NotNull Double signal1,

        /** New second replicate signal reading. */
        @NotNull Double signal2,

        /**
         * New nominal concentration for this pair.
         * May be {@code null} for control and unknown pairs that have no nominal value.
         */
        Double concentrationNominal
) {
}

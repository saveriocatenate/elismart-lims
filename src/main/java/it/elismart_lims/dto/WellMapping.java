package it.elismart_lims.dto;

import it.elismart_lims.model.PairType;

/**
 * Maps a single plate well to the {@link PairType} and optional nominal concentration
 * that should be assigned to the imported {@link it.elismart_lims.model.MeasurementPair}.
 *
 * @param pairType              classification of the well (CALIBRATION, CONTROL, or SAMPLE)
 * @param concentrationNominal  nominal concentration for the well; {@code null} for unknowns
 */
public record WellMapping(
        PairType pairType,
        Double concentrationNominal
) {
}

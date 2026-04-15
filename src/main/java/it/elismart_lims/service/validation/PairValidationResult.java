package it.elismart_lims.service.validation;

/**
 * Immutable result for a single {@link it.elismart_lims.model.MeasurementPair} evaluated
 * by {@link ValidationEngine}.
 *
 * <p>Produced for every non-outlier CONTROL and SAMPLE pair. Outlier pairs and
 * CALIBRATION pairs are excluded from validation and therefore produce no record.</p>
 *
 * @param pairId                    database ID of the evaluated {@code MeasurementPair}
 * @param cvPass                    {@code true} if the pair's {@code cvPct} is within the protocol limit
 * @param recoveryPass              {@code true} if the pair's {@code recoveryPct} is within the protocol limit
 * @param interpolatedConcentration back-calculated concentration derived from the calibration curve;
 *                                  {@code null} when {@code concentrationNominal ≤ 0} or when
 *                                  back-interpolation produced a negative result
 * @param calculatedRecovery        {@code (interpolatedConcentration / concentrationNominal) × 100};
 *                                  {@code null} when recovery was skipped or could not be computed
 * @param outOfCalibrationRange     {@code true} when back-interpolation returned a negative concentration,
 *                                  indicating the signal lies outside the calibration curve range
 */
public record PairValidationResult(
        Long pairId,
        boolean cvPass,
        boolean recoveryPass,
        Double interpolatedConcentration,
        Double calculatedRecovery,
        boolean outOfCalibrationRange
) {}

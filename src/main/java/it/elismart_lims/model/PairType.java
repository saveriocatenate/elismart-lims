package it.elismart_lims.model;

/**
 * Classification of a {@link MeasurementPair} within an experiment run.
 *
 * <ul>
 *   <li>{@link #CALIBRATION} – standard curve point used to build the calibration model</li>
 *   <li>{@link #CONTROL} – quality-control sample with known concentration</li>
 *   <li>{@link #SAMPLE} – unknown patient or research sample</li>
 * </ul>
 */
public enum PairType {
    CALIBRATION,
    CONTROL,
    SAMPLE
}

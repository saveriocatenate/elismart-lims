package it.elismart_lims.model;

/**
 * Computed quality-control status of a single {@link MeasurementPair} within a validated experiment.
 *
 * <ul>
 *   <li>{@link #PASS} – pair is within protocol limits for both %CV and %Recovery.</li>
 *   <li>{@link #FAIL} – pair exceeds at least one protocol acceptance limit.</li>
 *   <li>{@link #OUTLIER} – pair has been flagged as an outlier (automatically or manually).</li>
 *   <li>{@link #PENDING} – experiment has not yet been validated; no status can be assigned.</li>
 * </ul>
 *
 * <p>The frontend renders {@code PASS} as ✅, {@code FAIL} as ❌, and anything else as —.
 * {@code OUTLIER} and {@code PENDING} are therefore displayed as — in the status column;
 * outlier pairs are indicated separately via the {@code isOutlier} flag.</p>
 */
public enum PairStatus {

    /** Pair is valid: not an outlier, and %CV and %Recovery are within protocol limits. */
    PASS,

    /** Pair exceeds at least one protocol acceptance limit (%CV or %Recovery). */
    FAIL,

    /**
     * Pair has been flagged as an outlier (by the automated Grubbs test or manual override).
     * Outlier pairs are excluded from PASS/FAIL evaluation.
     */
    OUTLIER,

    /**
     * The owning experiment has not yet completed validation (status is PENDING, COMPLETED,
     * or VALIDATION_ERROR), so no per-pair status can be determined.
     */
    PENDING
}

package it.elismart_lims.service.validation;

/**
 * Static utility class for derived metric formulas used by the validation engine.
 *
 * <p>All formulas are compliant with ISO 5725 / CLSI EP15-A3 for duplicate measurements
 * (n=2 replicates). Using centralised constants and methods prevents formula drift
 * across creation and update paths.</p>
 */
public final class ValidationConstants {

    /**
     * Square root of 2 — used in the ISO 5725 sample standard deviation formula for n=2.
     * Pre-computed to avoid repeated {@link Math#sqrt} calls.
     */
    public static final double SQRT_2 = Math.sqrt(2.0);

    private ValidationConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Calculates the arithmetic mean of two replicate signals.
     *
     * <p>Formula: mean = (signal1 + signal2) / 2</p>
     *
     * @param signal1 first replicate raw signal
     * @param signal2 second replicate raw signal
     * @return arithmetic mean of the two signals
     */
    public static double calculateSignalMean(double signal1, double signal2) {
        return (signal1 + signal2) / 2.0;
    }

    /**
     * Calculates the percent coefficient of variation (%CV) for a duplicate measurement.
     *
     * <p>Formula (ISO 5725 / CLSI EP15-A3, n=2):</p>
     * <pre>
     *   SD  = |signal1 − signal2| / √2       (sample standard deviation for n=2)
     *   %CV = (SD / mean) × 100
     * </pre>
     *
     * <p>The division by √2 (rather than by 2, which would give a simple percent-range)
     * is required by ISO 5725-2 and CLSI EP15-A3 to obtain an unbiased estimate of the
     * population standard deviation from a pair of replicates.</p>
     *
     * <p>Returns {@code 0.0} when the mean is zero to avoid division by zero.</p>
     *
     * @param signal1 first replicate raw signal
     * @param signal2 second replicate raw signal
     * @return %CV as a percentage value (e.g. {@code 6.73} means 6.73%)
     */
    public static double calculateCvPercent(double signal1, double signal2) {
        double mean = calculateSignalMean(signal1, signal2);
        if (mean == 0.0) {
            return 0.0;
        }
        double sd = Math.abs(signal1 - signal2) / SQRT_2;
        return (sd / mean) * 100.0;
    }
}

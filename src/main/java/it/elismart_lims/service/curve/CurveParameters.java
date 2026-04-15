package it.elismart_lims.service.curve;

import java.util.Map;

/**
 * Immutable container for the fitted parameters of a calibration curve.
 *
 * <p>Parameter names are model-specific. For the 4PL model the keys are
 * {@code "A"}, {@code "B"}, {@code "C"}, and {@code "D"}. Consumers should
 * retrieve values via the constant keys exposed by the fitter implementation
 * (e.g. {@code FourPLFitter.PARAM_A}).</p>
 *
 * <p>Non-linear fitters (4PL, 5PL, 3PL) also include two diagnostic keys in the map,
 * prefixed with {@code _} to distinguish them from model parameters:</p>
 * <ul>
 *   <li>{@link #META_CONVERGENCE} — {@code 1.0} if the optimizer converged normally,
 *       {@code 0.0} if convergence was not achieved.</li>
 *   <li>{@link #META_RMS} — root-mean-square residual from the final fit.</li>
 * </ul>
 *
 * <p>Instances are produced by {@link CurveFitter#fit} and consumed by
 * {@link CurveFitter#interpolate}. They are also serialised as JSON to the
 * {@code curve_parameters} column on the {@code experiment} table.</p>
 *
 * @param values mutable or immutable map of parameter name → fitted numeric value
 */
public record CurveParameters(Map<String, Double> values) {

    /**
     * Map key for the optimizer convergence flag.
     * Value is {@code 1.0} (converged) or {@code 0.0} (not converged).
     * Present only for non-linear fitters (4PL, 5PL, 3PL).
     */
    public static final String META_CONVERGENCE = "_convergence";

    /**
     * Map key for the root-mean-square residual of the final fit.
     * Present only for non-linear fitters (4PL, 5PL, 3PL).
     */
    public static final String META_RMS = "_rms";
}

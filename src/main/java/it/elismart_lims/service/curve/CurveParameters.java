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

    /**
     * Map key for the coefficient of determination (pseudo-R²).
     * Value is {@code 1 - SS_res / SS_tot}, using unweighted residuals.
     * {@link Double#NaN} when all observed signals are identical (SS_tot < 1e-12).
     * Present for all fitter types.
     */
    public static final String META_R2 = "_r2";

    /**
     * Map key for the unweighted root-mean-square error: {@code sqrt(SS_res / n)}.
     * For nonlinear fitters this coexists with {@link #META_RMS} (the weighted optimizer RMS).
     * Present for all fitter types.
     */
    public static final String META_RMSE = "_rmse";

    /**
     * Map key for the residual degrees of freedom: {@code n − p}, where {@code n} is
     * the number of calibration points and {@code p} is the number of free parameters.
     * Present for all fitter types.
     */
    public static final String META_DF = "_df";
}

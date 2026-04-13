package it.elismart_lims.service.curve;

import java.util.List;

/**
 * Strategy interface for calibration curve fitting and back-interpolation.
 *
 * <p>Implementations encapsulate a specific mathematical model (4PL, 5PL, linear,
 * etc.) and expose two operations:</p>
 * <ol>
 *   <li>{@link #fit} — regresses the model onto a set of calibration points and
 *       returns the optimised parameter set.</li>
 *   <li>{@link #interpolate} — solves the inverse equation to recover concentration
 *       from an unknown signal, using previously fitted parameters.</li>
 * </ol>
 *
 * <p>Implementations must be stateless: all state is carried in the returned
 * {@link CurveParameters} record. The same instance may be reused across threads.</p>
 */
public interface CurveFitter {

    /**
     * Fits the calibration curve to the provided calibration points.
     *
     * @param points list of calibration points (concentration, signal pairs);
     *               must contain at least as many points as there are free parameters
     *               in the model
     * @return the optimized curve parameters
     * @throws IllegalArgumentException if {@code points} is {@code null}, empty, or
     *                                  contains fewer points than required by the model
     */
    CurveParameters fit(List<CalibrationPoint> points);

    /**
     * Back-calculates concentration from a measured signal using the fitted curve.
     *
     * <p>The signal must fall strictly within the asymptotic range of the fitted curve.
     * Signals at or beyond the asymptotes are undefined in the inverse equation and
     * result in an {@link IllegalArgumentException}.</p>
     *
     * @param signal the measured instrument signal for an unknown sample
     * @param params the fitted curve parameters produced by {@link #fit}
     * @return the interpolated concentration corresponding to {@code signal}
     * @throws IllegalArgumentException if {@code signal} is outside the interpolable
     *                                  range defined by the fitted asymptotes
     */
    double interpolate(double signal, CurveParameters params);
}

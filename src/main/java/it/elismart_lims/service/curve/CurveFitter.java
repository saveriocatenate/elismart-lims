package it.elismart_lims.service.curve;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** Shared logger for static utility methods on this interface. */
    Logger LOG = LoggerFactory.getLogger(CurveFitter.class);

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

    /**
     * Computes 1/signal² weights for Weighted Least Squares fitting.
     *
     * <p>Models signal variance as proportional to signal magnitude (proportional
     * error model), which is the standard assumption for heteroscedastic immunoassay data
     * across a wide dynamic range. Down-weighting high-signal calibrators reduces their leverage
     * on the fitted curve, correcting the EC50 bias introduced by heteroscedasticity.</p>
     *
     * <p>When {@code signal <= 0}, the fallback weight {@code 1.0} is used to avoid
     * division by zero.</p>
     *
     * @param points the calibration points whose signals drive the weight computation;
     *               must be the same list (and order) passed to {@link #fit}
     * @return array of per-point weights in the same order as {@code points}
     * @throws IllegalArgumentException if {@code points} is {@code null}
     */
    static double[] computeWeights(List<CalibrationPoint> points) {
        if (points == null) throw new IllegalArgumentException("points must not be null");
        return points.stream()
                .mapToDouble(p -> {
                    double signal = p.signal();
                    return signal > 0 ? 1.0 / (signal * signal) : 1.0;
                })
                .toArray();
    }

    /**
     * Computes unweighted goodness-of-fit metrics from observed vs predicted signals.
     *
     * <ul>
     *   <li><b>R²</b> = {@code 1 - SS_res / SS_tot}; {@link Double#NaN} when SS_tot &lt; 1e-12.</li>
     *   <li><b>RMSE</b> = {@code sqrt(SS_res / n)} (unweighted).</li>
     *   <li><b>df</b> = {@code n - nParams}.</li>
     * </ul>
     *
     * @param yActual    observed signal values; must be non-null and same length as {@code yPredicted}
     * @param yPredicted model-predicted signal values at the calibration concentrations
     * @param nParams    number of free parameters in the fitted model (e.g. 4 for 4PL)
     * @return map with keys {@link CurveParameters#META_R2}, {@link CurveParameters#META_RMSE},
     *         {@link CurveParameters#META_DF}
     * @throws IllegalArgumentException if either array is null or their lengths differ
     */
    static Map<String, Double> computeGoodnessOfFit(double[] yActual, double[] yPredicted, int nParams) {
        if (yActual == null || yPredicted == null) {
            throw new IllegalArgumentException("yActual and yPredicted must not be null");
        }
        if (yActual.length != yPredicted.length) {
            throw new IllegalArgumentException(
                    "yActual and yPredicted must have the same length; got "
                    + yActual.length + " vs " + yPredicted.length);
        }
        int n = yActual.length;
        double yMean = 0.0;
        for (double y : yActual) yMean += y;
        yMean /= n;

        double ssRes = 0.0;
        double ssTot = 0.0;
        for (int i = 0; i < n; i++) {
            double residual  = yActual[i] - yPredicted[i];
            double deviation = yActual[i] - yMean;
            ssRes += residual  * residual;
            ssTot += deviation * deviation;
        }

        double r2   = (ssTot < 1e-12) ? Double.NaN : (1.0 - ssRes / ssTot);
        double rmse = Math.sqrt(ssRes / n);

        Map<String, Double> result = new HashMap<>();
        result.put(CurveParameters.META_R2,   r2);
        result.put(CurveParameters.META_RMSE, rmse);
        result.put(CurveParameters.META_DF,   (double) (n - nParams));
        return result;
    }

    /**
     * Estimates an initial Hill slope (B₀) for nonlinear sigmoid fitters from the
     * calibration data, replacing the hardcoded {@code B₀ = 1.0} starting guess.
     *
     * <h2>Algorithm</h2>
     * <ol>
     *   <li>Sort (xData, yData) pairs by concentration.</li>
     *   <li>Compute the 10% and 90% response levels relative to the observed signal range.</li>
     *   <li>Linearly interpolate to find the concentrations x₁₀ and x₉₀ at those levels
     *       (direction-agnostic: works for both increasing and decreasing curves).</li>
     *   <li>Apply {@code B₀ = |log(81) / log(x₉₀ / x₁₀)|}. The {@code Math.abs()} ensures
     *       a positive estimate for competitive (decreasing-signal) assays where
     *       {@code log(x₉₀ / x₁₀)} is negative.</li>
     *   <li>Clamp to [0.1, 10.0].</li>
     * </ol>
     *
     * <h2>Fallback conditions → returns 1.0</h2>
     * <ul>
     *   <li>Fewer than 2 data points.</li>
     *   <li>Signal range &lt; 1e-12 (flat calibration curve).</li>
     *   <li>x₁₀ or x₉₀ cannot be bracketed in the data.</li>
     *   <li>x₁₀ or x₉₀ ≤ 0, or x₁₀ == x₉₀.</li>
     * </ul>
     *
     * @param xData concentration values (may be unsorted; must match {@code yData} by index)
     * @param yData signal values (must match {@code xData} by index)
     * @return estimated initial Hill slope in [0.1, 10.0], or {@code 1.0} on fallback
     */
    static double estimateHillSlope(double[] xData, double[] yData) {
        if (xData == null || yData == null || xData.length < 2 || xData.length != yData.length) {
            return 1.0;
        }

        int n = xData.length;

        // Sort pairs by concentration
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(xData[a], xData[b]));
        double[] sx = new double[n];
        double[] sy = new double[n];
        for (int i = 0; i < n; i++) {
            sx[i] = xData[idx[i]];
            sy[i] = yData[idx[i]];
        }

        // Signal range (search full array — signal may not be monotone after sorting by x)
        double yMin = sy[0];
        double yMax = sy[0];
        for (double y : sy) {
            if (y < yMin) yMin = y;
            if (y > yMax) yMax = y;
        }
        if (Math.abs(yMax - yMin) < 1e-12) {
            return 1.0;
        }

        double y10 = yMin + 0.10 * (yMax - yMin);
        double y90 = yMin + 0.90 * (yMax - yMin);

        double x10 = interpolateConcentrationAtLevel(sx, sy, y10);
        double x90 = interpolateConcentrationAtLevel(sx, sy, y90);

        if (x10 <= 0.0 || x90 <= 0.0 || x10 == x90) {
            return 1.0;
        }

        double b0 = Math.abs(Math.log(81.0) / Math.log(x90 / x10));
        if (Double.isNaN(b0) || Double.isInfinite(b0)) return 1.0;
        b0 = Math.max(0.1, Math.min(b0, 10.0));
        LOG.debug("Estimated initial Hill slope B₀ = {} from calibration data", b0);
        return b0;
    }

    /**
     * Linearly interpolates the concentration at which the signal equals {@code targetY},
     * scanning adjacent pairs in the concentration-sorted arrays.
     * Direction-agnostic: finds brackets for both increasing and decreasing signal curves.
     *
     * @param sortedX concentration values sorted in ascending order
     * @param sortedY signal values corresponding to {@code sortedX}
     * @param targetY target signal level to interpolate at
     * @return interpolated concentration, or {@code -1.0} if no bracket is found
     */
    private static double interpolateConcentrationAtLevel(
            double[] sortedX, double[] sortedY, double targetY) {
        for (int i = 0; i < sortedX.length - 1; i++) {
            double y1 = sortedY[i];
            double y2 = sortedY[i + 1];
            if ((y1 <= targetY && targetY <= y2) || (y2 <= targetY && targetY <= y1)) {
                if (Math.abs(y2 - y1) < 1e-12) continue;
                double t = (targetY - y1) / (y2 - y1);
                return sortedX[i] + t * (sortedX[i + 1] - sortedX[i]);
            }
        }
        return -1.0;
    }
}

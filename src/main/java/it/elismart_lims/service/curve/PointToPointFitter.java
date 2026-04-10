package it.elismart_lims.service.curve;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-parametric point-to-point calibration curve fitter.
 *
 * <h2>Model</h2>
 * <p>No mathematical model is fitted. The calibration table is stored verbatim and
 * back-interpolation uses piecewise linear interpolation between adjacent sorted
 * calibration points.</p>
 *
 * <h2>Parameters stored in {@link CurveParameters}</h2>
 * <ul>
 *   <li>{@code "n"} — number of calibration points</li>
 *   <li>{@code "x_i"}, {@code "y_i"} — concentration and signal for the i-th point
 *       (0-indexed, sorted ascending by concentration)</li>
 * </ul>
 *
 * <h2>Back-interpolation</h2>
 * <p>Given a signal value, the method locates the two adjacent calibration points
 * that bracket the signal (assumes a monotonic signal-to-concentration relationship)
 * and returns the linearly interpolated concentration:</p>
 * <pre>
 *   x = x_i + (signal − y_i) · (x_{i+1} − x_i) / (y_{i+1} − y_i)
 * </pre>
 *
 * <p><b>Not recommended for high-precision analysis.</b> Use 4PL or 5PL for ELISA data.</p>
 */
public class PointToPointFitter implements CurveFitter {

    /** Minimum number of calibration points required for piecewise interpolation. */
    private static final int MIN_POINTS = 2;

    /**
     * {@inheritDoc}
     *
     * <p>Points are sorted ascending by concentration. The resulting {@link CurveParameters}
     * stores the full calibration table as indexed keys so it can be serialised to JSON.</p>
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are provided
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "Point-to-point fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        List<CalibrationPoint> sorted = points.stream()
                .sorted(Comparator.comparingDouble(CalibrationPoint::concentration))
                .toList();

        Map<String, Double> map = new HashMap<>();
        map.put("n", (double) sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            map.put("x_" + i, sorted.get(i).concentration());
            map.put("y_" + i, sorted.get(i).signal());
        }

        return new CurveParameters(Map.copyOf(map));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Finds the pair of adjacent calibration points that bracket {@code signal}
     * and returns the linearly interpolated concentration. The curve must be
     * monotonic over the calibration range.</p>
     *
     * @throws IllegalArgumentException if the signal is outside the range covered by
     *         the calibration table, or if no monotonic bracket can be found
     */
    @Override
    public double interpolate(double signal, CurveParameters params) {
        int n = (int) params.values().get("n").doubleValue();

        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = params.values().get("x_" + i);
            ys[i] = params.values().get("y_" + i);
        }

        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double y : ys) {
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }

        if (signal < minY || signal > maxY) {
            throw new IllegalArgumentException(String.format(
                    "Signal %.6f is outside the calibration range [%.6f, %.6f].",
                    signal, minY, maxY));
        }

        // Find bracketing interval: signal between ys[i] and ys[i+1]
        for (int i = 0; i < n - 1; i++) {
            double yLow = Math.min(ys[i], ys[i + 1]);
            double yHigh = Math.max(ys[i], ys[i + 1]);
            if (signal >= yLow && signal <= yHigh) {
                double dy = ys[i + 1] - ys[i];
                if (Math.abs(dy) < 1e-12) {
                    // Flat segment — return midpoint concentration
                    return (xs[i] + xs[i + 1]) / 2.0;
                }
                return xs[i] + (signal - ys[i]) * (xs[i + 1] - xs[i]) / dy;
            }
        }

        throw new IllegalArgumentException(String.format(
                "No monotonic bracket found for signal %.6f in the calibration table. "
                + "Ensure the calibration curve is monotonic.", signal));
    }
}

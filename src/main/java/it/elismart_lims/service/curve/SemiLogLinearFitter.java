package it.elismart_lims.service.curve;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Semi-log linear calibration curve fitter.
 *
 * <h2>Model</h2>
 * <pre>
 *   y = m·ln(x) + q
 * </pre>
 * <ul>
 *   <li><b>m</b> — slope on the natural-log concentration axis</li>
 *   <li><b>q</b> — intercept</li>
 * </ul>
 *
 * <h2>Fitting</h2>
 * <p>Concentration values are log-transformed before ordinary least-squares regression
 * via {@link SimpleRegression} from Apache Commons Math 3.</p>
 *
 * <h2>Back-interpolation</h2>
 * <pre>
 *   ln(x) = (y − q) / m  →  x = exp((y − q) / m)
 * </pre>
 */
public class SemiLogLinearFitter implements CurveFitter {

    /** Minimum number of points required to determine two parameters. */
    private static final int MIN_POINTS = 2;

    /** Map key for the slope parameter m. */
    public static final String PARAM_M = "m";

    /** Map key for the intercept parameter q. */
    public static final String PARAM_Q = "q";

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are supplied,
     *         or if any concentration value is ≤ 0 (log is undefined)
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "Semi-log linear curve fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        SimpleRegression regression = new SimpleRegression();
        for (CalibrationPoint p : points) {
            if (p.concentration() <= 0.0) {
                throw new IllegalArgumentException(
                        "Semi-log linear fitting requires all concentrations > 0; got: "
                        + p.concentration());
            }
            regression.addData(Math.log(p.concentration()), p.signal());
        }

        double m = regression.getSlope();
        double q = regression.getIntercept();
        int n = points.size();

        double[] yActual    = new double[n];
        double[] yPredicted = new double[n];
        for (int i = 0; i < n; i++) {
            yActual[i]    = points.get(i).signal();
            yPredicted[i] = m * Math.log(points.get(i).concentration()) + q;
        }

        Map<String, Double> resultParams = new HashMap<>();
        resultParams.put(PARAM_M, m);
        resultParams.put(PARAM_Q, q);
        resultParams.putAll(CurveFitter.computeGoodnessOfFit(yActual, yPredicted, 2));
        return new CurveParameters(resultParams);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inverse formula: {@code x = exp((y − q) / m)}</p>
     *
     * @throws IllegalArgumentException if slope m ≈ 0, making inversion undefined
     */
    @Override
    public double interpolate(double signal, CurveParameters params) {
        double m = params.values().get(PARAM_M);
        double q = params.values().get(PARAM_Q);

        if (Math.abs(m) < 1e-12) {
            throw new IllegalArgumentException(
                    "Cannot back-calculate concentration: slope m ≈ 0, the model is degenerate.");
        }

        double result = Math.exp((signal - q) / m);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation produced invalid result (NaN/Infinity) for signal %.6f "
                    + "(m=%.6e, q=%.6f). "
                    + "The signal may be far outside the calibrated range.",
                    signal, m, q));
        }
        return result;
    }
}

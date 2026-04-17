package it.elismart_lims.service.curve;

import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linear calibration curve fitter.
 *
 * <h2>Model</h2>
 * <pre>
 *   y = m·x + q
 * </pre>
 * <ul>
 *   <li><b>m</b> — slope</li>
 *   <li><b>q</b> — intercept</li>
 * </ul>
 *
 * <h2>Fitting</h2>
 * <p>Ordinary least-squares via {@link SimpleRegression} from Apache Commons Math 3.</p>
 *
 * <h2>Back-interpolation</h2>
 * <pre>
 *   x = (y − q) / m
 * </pre>
 */
public class LinearFitter implements CurveFitter {

    /** Minimum number of points required to determine two parameters. */
    private static final int MIN_POINTS = 2;

    /** Map key for the slope parameter m. */
    public static final String PARAM_M = "m";

    /** Map key for the intercept parameter q. */
    public static final String PARAM_Q = "q";

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are supplied
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "Linear curve fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        SimpleRegression regression = new SimpleRegression();
        for (CalibrationPoint p : points) {
            regression.addData(p.concentration(), p.signal());
        }

        double m = regression.getSlope();
        double q = regression.getIntercept();
        int n = points.size();

        double[] yActual    = new double[n];
        double[] yPredicted = new double[n];
        for (int i = 0; i < n; i++) {
            yActual[i]    = points.get(i).signal();
            yPredicted[i] = m * points.get(i).concentration() + q;
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
     * <p>Inverse formula: {@code x = (y − q) / m}</p>
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

        return (signal - q) / m;
    }
}

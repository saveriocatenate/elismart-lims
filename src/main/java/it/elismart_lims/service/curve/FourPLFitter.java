package it.elismart_lims.service.curve;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.util.Pair;

import java.util.List;
import java.util.Map;

/**
 * Four-Parameter Logistic (4PL) calibration curve fitter.
 *
 * <h2>Model</h2>
 * <pre>
 *   y = D + (A - D) / (1 + (x / C)^B)
 * </pre>
 * <ul>
 *   <li><b>A</b> — bottom asymptote: signal as concentration → 0</li>
 *   <li><b>B</b> — slope (Hill coefficient): steepness of the sigmoid</li>
 *   <li><b>C</b> — inflection point (EC50 / IC50): concentration at half-maximal signal</li>
 *   <li><b>D</b> — top asymptote: signal as concentration → ∞</li>
 * </ul>
 *
 * <h2>Fitting</h2>
 * <p>Uses the Levenberg-Marquardt nonlinear least-squares algorithm from
 * Apache Commons Math 3. An analytic Jacobian is provided to improve convergence.</p>
 *
 * <h2>Back-interpolation (inverse)</h2>
 * <pre>
 *   x = C · ((A - D) / (y - D) - 1)^(1/B)
 * </pre>
 * <p>The signal must be strictly inside the range (min(A,D), max(A,D)); values at
 * or beyond the asymptotes make the inverse undefined.</p>
 */
public class FourPLFitter implements CurveFitter {

    /** Minimum number of calibration points required to fit 4 free parameters. */
    private static final int MIN_POINTS = 4;

    /** Maximum optimizer evaluations. */
    private static final int MAX_EVALUATIONS = 10_000;

    /** Maximum optimizer iterations. */
    private static final int MAX_ITERATIONS = 10_000;

    /** Map key for the bottom asymptote parameter A. */
    public static final String PARAM_A = "A";

    /** Map key for the slope parameter B. */
    public static final String PARAM_B = "B";

    /** Map key for the inflection-point parameter C. */
    public static final String PARAM_C = "C";

    /** Map key for the top asymptote parameter D. */
    public static final String PARAM_D = "D";

    /**
     * {@inheritDoc}
     *
     * <p>The initial parameter guess is derived automatically from the data:</p>
     * <ul>
     *   <li>A₀ = min(signal values)</li>
     *   <li>D₀ = max(signal values)</li>
     *   <li>C₀ = geometric mean of min/max concentration</li>
     *   <li>B₀ = 1.0</li>
     * </ul>
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are provided
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "4PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        double[] xData = points.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = points.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double[] initialGuess = buildInitialGuess(xData, yData);

        MultivariateJacobianFunction model = params -> {
            double a = params.getEntry(0);
            double b = params.getEntry(1);
            double c = params.getEntry(2);
            double d = params.getEntry(3);

            double[] modelValues = new double[xData.length];
            double[][] jacobianData = new double[xData.length][4];

            for (int i = 0; i < xData.length; i++) {
                double x = xData[i];
                double ratio = Math.pow(x / c, b);
                double denom = 1.0 + ratio;
                double denomSq = denom * denom;

                // y = D + (A - D) / (1 + (x/C)^B)
                modelValues[i] = d + (a - d) / denom;

                // ∂y/∂A
                jacobianData[i][0] = 1.0 / denom;
                // ∂y/∂B  — ln(x/C) is 0 when x≤0 or C≤0 (degenerate case)
                double lnRatio = (x > 0.0 && c > 0.0) ? Math.log(x / c) : 0.0;
                jacobianData[i][1] = -(a - d) * ratio * lnRatio / denomSq;
                // ∂y/∂C
                jacobianData[i][2] = (a - d) * b * ratio / (c * denomSq);
                // ∂y/∂D
                jacobianData[i][3] = 1.0 - 1.0 / denom;
            }

            return Pair.create(
                    new ArrayRealVector(modelValues, false),
                    new Array2DRowRealMatrix(jacobianData, false));
        };

        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(initialGuess)
                .model(model)
                .target(yData)
                .lazyEvaluation(false)
                .maxEvaluations(MAX_EVALUATIONS)
                .maxIterations(MAX_ITERATIONS)
                .build();

        LeastSquaresOptimizer.Optimum optimum = new LevenbergMarquardtOptimizer().optimize(problem);
        double[] fitted = optimum.getPoint().toArray();

        return new CurveParameters(Map.of(
                PARAM_A, fitted[0],
                PARAM_B, fitted[1],
                PARAM_C, fitted[2],
                PARAM_D, fitted[3]
        ));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inverse formula: {@code x = C · ((A − D) / (y − D) − 1)^(1/B)}</p>
     *
     * @throws IllegalArgumentException if {@code signal} ≤ min(A, D) or ≥ max(A, D),
     *         since the inverse is undefined at or beyond the asymptotes
     */
    @Override
    public double interpolate(double signal, CurveParameters params) {
        double a = params.values().get(PARAM_A);
        double b = params.values().get(PARAM_B);
        double c = params.values().get(PARAM_C);
        double d = params.values().get(PARAM_D);

        double lo = Math.min(a, d);
        double hi = Math.max(a, d);

        if (signal <= lo || signal >= hi) {
            throw new IllegalArgumentException(String.format(
                    "Signal %.6f is outside the interpolable range (%.6f, %.6f). "
                    + "The signal must be strictly between the two asymptotes.",
                    signal, lo, hi));
        }

        // Inverse 4PL: x = C · ((A - D) / (y - D) - 1)^(1/B)
        double innerRatio = (a - d) / (signal - d) - 1.0;

        if (innerRatio <= 0.0) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation failed: intermediate ratio %.6f ≤ 0 for signal %.6f. "
                    + "The signal may be at or very close to an asymptote.",
                    innerRatio, signal));
        }

        return c * Math.pow(innerRatio, 1.0 / b);
    }

    /**
     * Builds a data-driven initial parameter guess for the LM optimizer.
     *
     * <p>Formula:</p>
     * <ul>
     *   <li>A₀ = min(y) — bottom asymptote estimate</li>
     *   <li>D₀ = max(y) — top asymptote estimate</li>
     *   <li>C₀ = √(xMin · xMax) — geometric mean of concentration range</li>
     *   <li>B₀ = 1.0 — neutral starting slope</li>
     * </ul>
     *
     * @param xData array of concentration values
     * @param yData array of signal values
     * @return initial parameter array in order [A, B, C, D]
     */
    private double[] buildInitialGuess(double[] xData, double[] yData) {
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;

        for (int i = 0; i < xData.length; i++) {
            if (yData[i] < minY) minY = yData[i];
            if (yData[i] > maxY) maxY = yData[i];
            if (xData[i] < minX) minX = xData[i];
            if (xData[i] > maxX) maxX = xData[i];
        }

        double initialC = (minX > 0 && maxX > 0)
                ? Math.sqrt(minX * maxX)
                : (minX + maxX) / 2.0;

        return new double[]{minY, 1.0, initialC, maxY};
    }
}

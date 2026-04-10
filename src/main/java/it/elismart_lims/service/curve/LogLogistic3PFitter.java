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
 * Three-Parameter Log-Logistic (3PL) calibration curve fitter.
 *
 * <h2>Model</h2>
 * <p>Equivalent to 4PL with the bottom asymptote A fixed at zero:</p>
 * <pre>
 *   y = D / (1 + (x / C)^B)
 *     = D · (x/C)^B / (1 + (x/C)^B)   (equivalent form)
 * </pre>
 * <ul>
 *   <li><b>B</b> — slope (Hill coefficient)</li>
 *   <li><b>C</b> — inflection point (EC50 / IC50)</li>
 *   <li><b>D</b> — top asymptote (signal at concentration → ∞)</li>
 * </ul>
 *
 * <h2>Fitting</h2>
 * <p>Levenberg-Marquardt with analytic Jacobian via Apache Commons Math 3.</p>
 *
 * <h2>Back-interpolation</h2>
 * <pre>
 *   x = C · (y / (D − y))^(1/B)
 * </pre>
 */
public class LogLogistic3PFitter implements CurveFitter {

    /** Minimum number of calibration points required to fit 3 free parameters. */
    private static final int MIN_POINTS = 3;

    /** Maximum optimizer evaluations. */
    private static final int MAX_EVALUATIONS = 10_000;

    /** Maximum optimizer iterations. */
    private static final int MAX_ITERATIONS = 10_000;

    /** Map key for the slope parameter B. */
    public static final String PARAM_B = "B";

    /** Map key for the inflection-point parameter C. */
    public static final String PARAM_C = "C";

    /** Map key for the top asymptote parameter D. */
    public static final String PARAM_D = "D";

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are provided
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "3PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        double[] xData = points.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = points.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double[] initialGuess = buildInitialGuess(xData, yData);

        MultivariateJacobianFunction model = params -> {
            double b = params.getEntry(0);
            double c = params.getEntry(1);
            double d = params.getEntry(2);

            double[] modelValues = new double[xData.length];
            double[][] jacobianData = new double[xData.length][3];

            for (int i = 0; i < xData.length; i++) {
                double x = xData[i];
                double ratio = Math.pow(x / c, b);
                double denom = 1.0 + ratio;
                double denomSq = denom * denom;

                // y = D * ratio / (1 + ratio)
                modelValues[i] = d * ratio / denom;

                double lnXoverC = (x > 0.0 && c > 0.0) ? Math.log(x / c) : 0.0;

                // ∂y/∂B = D · ratio · ln(x/C) / (1 + ratio)^2
                jacobianData[i][0] = d * ratio * lnXoverC / denomSq;
                // ∂y/∂C = D · (-B · ratio) / (C · (1 + ratio)^2)
                jacobianData[i][1] = d * (-b * ratio) / (c * denomSq);
                // ∂y/∂D = ratio / (1 + ratio)
                jacobianData[i][2] = ratio / denom;
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
                PARAM_B, fitted[0],
                PARAM_C, fitted[1],
                PARAM_D, fitted[2]
        ));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inverse formula: {@code x = C · (y / (D − y))^(1/B)}</p>
     *
     * @throws IllegalArgumentException if signal is outside the range (0, D)
     */
    @Override
    public double interpolate(double signal, CurveParameters params) {
        double b = params.values().get(PARAM_B);
        double c = params.values().get(PARAM_C);
        double d = params.values().get(PARAM_D);

        if (signal <= 0.0 || signal >= d) {
            throw new IllegalArgumentException(String.format(
                    "Signal %.6f is outside the interpolable range (0, %.6f). "
                    + "The signal must be strictly between the zero asymptote and D.",
                    signal, d));
        }

        return c * Math.pow(signal / (d - signal), 1.0 / b);
    }

    /**
     * Builds a data-driven initial parameter guess.
     *
     * @param xData concentration values
     * @param yData signal values
     * @return initial parameter array in order [B, C, D]
     */
    private double[] buildInitialGuess(double[] xData, double[] yData) {
        double maxY = Double.MIN_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;

        for (int i = 0; i < xData.length; i++) {
            if (yData[i] > maxY) maxY = yData[i];
            if (xData[i] < minX) minX = xData[i];
            if (xData[i] > maxX) maxX = xData[i];
        }

        double initialC = (minX > 0 && maxX > 0)
                ? Math.sqrt(minX * maxX)
                : (minX + maxX) / 2.0;

        return new double[]{1.0, initialC, maxY * 1.1};
    }
}

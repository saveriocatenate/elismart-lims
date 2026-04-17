package it.elismart_lims.service.curve;

import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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

    private static final Logger log = LoggerFactory.getLogger(LogLogistic3PFitter.class);

    /** Minimum number of calibration points required to fit 3 free parameters. */
    private static final int MIN_POINTS = 3;

    /** Default maximum optimizer evaluations. */
    private static final int DEFAULT_MAX_EVALUATIONS = 10_000;

    /** Maximum optimizer iterations. */
    private static final int MAX_ITERATIONS = 10_000;

    /**
     * RMS residual threshold above which a warning is logged even if the optimizer converged.
     * A high RMS indicates the calibration data may not be well described by the 3PL model.
     */
    public static final double RMS_WARN_THRESHOLD = 0.1;

    /** Effective evaluation limit — overridable via the package-private constructor for testing. */
    private final int maxEvaluations;

    /** Creates a {@code LogLogistic3PFitter} with the default evaluation limit. */
    public LogLogistic3PFitter() {
        this.maxEvaluations = DEFAULT_MAX_EVALUATIONS;
    }

    /**
     * Package-private constructor for testing with a reduced evaluation limit.
     *
     * @param maxEvaluations maximum LM evaluations before the optimizer gives up
     */
    LogLogistic3PFitter(int maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
    }

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

        // Exclude zero-concentration points: ln(0/C) is -Infinity in the Jacobian,
        // which would corrupt the LM optimisation. Log a warning for each one skipped.
        List<CalibrationPoint> validPoints = points.stream()
                .filter(p -> {
                    if (p.concentration() <= 0.0) {
                        log.warn("3PL fitting: skipping calibration point with concentration {} "
                                + "(<= 0); ln(0/C) is undefined in the Jacobian.", p.concentration());
                        return false;
                    }
                    return true;
                })
                .toList();

        if (validPoints.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "3PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points with concentration > 0 after excluding zero-concentration "
                    + "points; got " + validPoints.size() + " valid points.");
        }

        double[] xData = validPoints.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = validPoints.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double[] initialGuess = buildInitialGuess(xData, yData);
        double[] weights = CurveFitter.computeWeights(validPoints);

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
                .weight(new DiagonalMatrix(weights))
                .lazyEvaluation(false)
                .maxEvaluations(maxEvaluations)
                .maxIterations(MAX_ITERATIONS)
                .build();

        LeastSquaresOptimizer.Optimum optimum;
        try {
            optimum = new LevenbergMarquardtOptimizer().optimize(problem);
        } catch (TooManyEvaluationsException e) {
            throw new IllegalStateException(
                    "Curve fitting did not converge after " + maxEvaluations
                    + " evaluations. The calibration data may not fit a 3PL model.", e);
        }

        double rms = optimum.getRMS();
        if (rms > RMS_WARN_THRESHOLD) {
            log.warn("3PL fit converged but RMS={} exceeds threshold {}. "
                    + "Calibration data may not be well described by the 3PL model.", rms, RMS_WARN_THRESHOLD);
        }

        double[] fitted = optimum.getPoint().toArray();

        Map<String, Double> resultParams = new HashMap<>();
        resultParams.put(PARAM_B, fitted[0]);
        resultParams.put(PARAM_C, fitted[1]);
        resultParams.put(PARAM_D, fitted[2]);
        resultParams.put(CurveParameters.META_CONVERGENCE, 1.0);
        resultParams.put(CurveParameters.META_RMS, rms);
        return new CurveParameters(resultParams);
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

        double result = c * Math.pow(signal / (d - signal), 1.0 / b);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation produced invalid result (NaN/Infinity) for signal %.6f "
                    + "(D=%.6f, B=%.6f, C=%.6f). "
                    + "The signal may be outside the curve's valid range.",
                    signal, d, b, c));
        }
        return result;
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

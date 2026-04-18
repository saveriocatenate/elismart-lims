package it.elismart_lims.service.curve;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
 * Apache Commons Math 3 with WLS (weighted least-squares) using 1/y² weights.
 * An analytic Jacobian is provided to improve convergence.</p>
 *
 * <h2>Back-interpolation (inverse)</h2>
 * <pre>
 *   x = C · ((A - D) / (y - D) - 1)^(1/B)
 * </pre>
 * <p>The signal must be strictly inside the range (min(A,D), max(A,D)); values at
 * or beyond the asymptotes make the inverse undefined.</p>
 */
public class FourPLFitter implements CurveFitter {

    private static final Logger log = LoggerFactory.getLogger(FourPLFitter.class);

    /** Minimum number of calibration points required to fit 4 free parameters. */
    private static final int MIN_POINTS = 4;

    /** Default maximum optimizer evaluations. */
    private static final int DEFAULT_MAX_EVALUATIONS = 10_000;

    /** Maximum optimizer iterations. */
    private static final int MAX_ITERATIONS = 10_000;

    /**
     * RMS residual threshold above which a warning is logged even if the optimizer converged.
     * A high RMS indicates the calibration data may not be well described by the 4PL model.
     * Configurable per-class as a named constant.
     */
    public static final double RMS_WARN_THRESHOLD = 0.1;

    /** Effective evaluation limit — overridable via the package-private constructor for testing. */
    private final int maxEvaluations;

    /** Creates a {@code FourPLFitter} with the default evaluation limit. */
    public FourPLFitter() {
        this.maxEvaluations = DEFAULT_MAX_EVALUATIONS;
    }

    /**
     * Package-private constructor for testing with a reduced evaluation limit.
     * Allows unit tests to force a {@code TooManyEvaluationsException} without
     * waiting for 10 000 iterations.
     *
     * @param maxEvaluations maximum LM evaluations before the optimizer gives up
     */
    FourPLFitter(int maxEvaluations) {
        this.maxEvaluations = maxEvaluations;
    }

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
     *   <li>B₀ = data-driven Hill slope estimate via {@link CurveFitter#estimateHillSlope}
     *       (falls back to 1.0 when data are insufficient)</li>
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

        // Exclude zero-concentration points: ln(0/C) is -Infinity in the Jacobian,
        // which would corrupt the LM optimisation. Log a warning for each one skipped.
        List<CalibrationPoint> validPoints = points.stream()
                .filter(p -> {
                    if (p.concentration() <= 0.0) {
                        log.warn("4PL fitting: skipping calibration point with concentration {} "
                                + "(<= 0); ln(0/C) is undefined in the Jacobian.", p.concentration());
                        return false;
                    }
                    return true;
                })
                .toList();

        if (validPoints.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "4PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points with concentration > 0 after excluding zero-concentration "
                    + "points; got " + validPoints.size() + " valid points.");
        }

        double[] xData = validPoints.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = validPoints.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double[] initialGuess = buildInitialGuess(xData, yData);
        double[] weights = CurveFitter.computeWeights(validPoints);

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
                    + " evaluations. The calibration data may not fit a 4PL model.", e);
        }

        double rms = optimum.getRMS();
        if (rms > RMS_WARN_THRESHOLD) {
            log.warn("4PL fit converged but RMS={} exceeds threshold {}. "
                    + "Calibration data may not be well described by the 4PL model.", rms, RMS_WARN_THRESHOLD);
        }

        double[] fitted = optimum.getPoint().toArray();

        Map<String, Double> resultParams = new HashMap<>();
        resultParams.put(PARAM_A, fitted[0]);
        resultParams.put(PARAM_B, fitted[1]);
        resultParams.put(PARAM_C, fitted[2]);
        resultParams.put(PARAM_D, fitted[3]);
        resultParams.put(CurveParameters.META_CONVERGENCE, 1.0);
        resultParams.put(CurveParameters.META_RMS, rms);

        // Re-evaluate model with fitted parameters (unweighted) to compute gof metrics
        double a = fitted[0], b = fitted[1], c = fitted[2], d = fitted[3];
        double[] yPredicted = new double[xData.length];
        for (int i = 0; i < xData.length; i++) {
            double ratio = Math.pow(xData[i] / c, b);
            yPredicted[i] = d + (a - d) / (1.0 + ratio);
            if (Double.isNaN(yPredicted[i]) || Double.isInfinite(yPredicted[i])) {
                throw new IllegalArgumentException(String.format(
                        "GoF re-evaluation produced NaN/Infinite at index %d "
                        + "(x=%.6f, c=%.6f, b=%.6f). Fitted parameters may be degenerate.",
                        i, xData[i], c, b));
            }
        }
        resultParams.putAll(CurveFitter.computeGoodnessOfFit(yData, yPredicted, 4));

        // 95% CI for EC50 (parameter C, index 2 in [A, B, C, D]).
        // sigma² = weighted RMS² × n / df rescales the Fisher information matrix to the
        // empirical residual variance, so the CI narrows for near-perfect data and widens
        // proportionally to residual scatter.
        int df = xData.length - 4;
        if (df > 0) {
            try {
                RealMatrix cov = optimum.getCovariances(1e-10);
                double sigma2 = optimum.getRMS() * optimum.getRMS() * xData.length / df;
                double seC = Math.sqrt(cov.getEntry(2, 2) * sigma2);
                double tValue = new TDistribution(df).inverseCumulativeProbability(0.975);
                resultParams.put(CurveParameters.META_EC50_LOWER95, fitted[2] - tValue * seC);
                resultParams.put(CurveParameters.META_EC50_UPPER95, fitted[2] + tValue * seC);
            } catch (Exception e) {
                log.warn("4PL: EC50 CI calculation failed (covariance matrix may be singular): {}", e.getMessage());
                resultParams.put(CurveParameters.META_EC50_LOWER95, null);
                resultParams.put(CurveParameters.META_EC50_UPPER95, null);
            }
        } else {
            log.warn("4PL: EC50 CI skipped — insufficient degrees of freedom (n={}, p=4).", xData.length);
            resultParams.put(CurveParameters.META_EC50_LOWER95, null);
            resultParams.put(CurveParameters.META_EC50_UPPER95, null);
        }

        return new CurveParameters(resultParams);
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

        double result = c * Math.pow(innerRatio, 1.0 / b);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation produced invalid result (NaN/Infinity) for signal %.6f "
                    + "(innerRatio=%.6f, B=%.6f, C=%.6f). "
                    + "The signal may be outside the curve's valid range.",
                    signal, innerRatio, b, c));
        }
        return result;
    }

    /**
     * Builds a data-driven initial parameter guess for the LM optimizer.
     *
     * <p>Formula:</p>
     * <ul>
     *   <li>A₀ = min(y) — bottom asymptote estimate</li>
     *   <li>B₀ = estimated Hill slope from 10%/90% signal response levels
     *       via {@link CurveFitter#estimateHillSlope}; falls back to 1.0 when
     *       the data are insufficient to form a reliable estimate</li>
     *   <li>C₀ = √(xMin · xMax) — geometric mean of concentration range</li>
     *   <li>D₀ = max(y) — top asymptote estimate</li>
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

        return new double[]{minY, CurveFitter.estimateHillSlope(xData, yData), initialC, maxY};
    }
}

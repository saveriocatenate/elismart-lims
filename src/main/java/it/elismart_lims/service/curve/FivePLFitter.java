package it.elismart_lims.service.curve;

import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.util.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Five-Parameter Logistic (5PL) calibration curve fitter.
 *
 * <h2>Model</h2>
 * <pre>
 *   y = D + (A − D) / (1 + (x / C)^B)^E
 * </pre>
 * <ul>
 *   <li><b>A</b> — bottom asymptote: signal as concentration → 0</li>
 *   <li><b>B</b> — slope (Hill coefficient): steepness of the sigmoid</li>
 *   <li><b>C</b> — inflection point (EC50 / IC50): concentration at half-maximal signal</li>
 *   <li><b>D</b> — top asymptote: signal as concentration → ∞</li>
 *   <li><b>E</b> — asymmetry parameter; when E = 1 the model reduces to 4PL</li>
 * </ul>
 *
 * <h2>Fitting</h2>
 * <p>Uses the Levenberg-Marquardt nonlinear least-squares algorithm from
 * Apache Commons Math 3. An analytic Jacobian is provided to improve convergence.
 * The initial guess sets E₀ = 1.0, which starts the optimizer at the symmetric
 * 4PL solution and lets it converge toward the correct asymmetry.</p>
 *
 * <h2>Back-interpolation (inverse)</h2>
 * <pre>
 *   x = C · (((A − D) / (y − D))^(1/E) − 1)^(1/B)
 * </pre>
 * <p>The signal must be strictly inside the range (min(A,D), max(A,D)); values at
 * or beyond the asymptotes make the inverse undefined.</p>
 */
public class FivePLFitter implements CurveFitter {

    private static final Logger log = LoggerFactory.getLogger(FivePLFitter.class);

    /** Minimum number of calibration points required to fit 5 free parameters. */
    private static final int MIN_POINTS = 5;

    /** Default maximum optimizer evaluations. */
    private static final int DEFAULT_MAX_EVALUATIONS = 10_000;

    /** Maximum optimizer iterations. */
    private static final int MAX_ITERATIONS = 10_000;

    /**
     * RMS residual threshold above which a warning is logged even if the optimizer converged.
     * A high RMS indicates the calibration data may not be well described by the 5PL model.
     */
    public static final double RMS_WARN_THRESHOLD = 0.1;

    /** Effective evaluation limit — overridable via the package-private constructor for testing. */
    private final int maxEvaluations;

    /** Creates a {@code FivePLFitter} with the default evaluation limit. */
    public FivePLFitter() {
        this.maxEvaluations = DEFAULT_MAX_EVALUATIONS;
    }

    /**
     * Package-private constructor for testing with a reduced evaluation limit.
     *
     * @param maxEvaluations maximum LM evaluations before the optimizer gives up
     */
    FivePLFitter(int maxEvaluations) {
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

    /** Map key for the asymmetry parameter E. */
    public static final String PARAM_E = "E";

    /**
     * {@inheritDoc}
     *
     * <p>The initial parameter guess is derived automatically from the data:</p>
     * <ul>
     *   <li>A₀ = min(signal values)</li>
     *   <li>D₀ = max(signal values)</li>
     *   <li>C₀ = geometric mean of min/max concentration</li>
     *   <li>B₀ = 1.0</li>
     *   <li>E₀ = 1.0 (symmetric starting point, equivalent to 4PL)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if fewer than {@value MIN_POINTS} points are provided
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "5PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points, got: " + (points == null ? 0 : points.size()));
        }

        // Exclude zero-concentration points: ln(0/C) is -Infinity in the Jacobian,
        // which would corrupt the LM optimisation. Log a warning for each one skipped.
        List<CalibrationPoint> validPoints = points.stream()
                .filter(p -> {
                    if (p.concentration() <= 0.0) {
                        log.warn("5PL fitting: skipping calibration point with concentration {} "
                                + "(<= 0); ln(0/C) is undefined in the Jacobian.", p.concentration());
                        return false;
                    }
                    return true;
                })
                .toList();

        if (validPoints.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "5PL curve fitting requires at least " + MIN_POINTS
                    + " calibration points with concentration > 0 after excluding zero-concentration "
                    + "points; got " + validPoints.size() + " valid points.");
        }

        double[] xData = validPoints.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = validPoints.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double[] initialGuess = buildInitialGuess(xData, yData);

        MultivariateJacobianFunction model = params -> {
            double a = params.getEntry(0);
            double b = params.getEntry(1);
            double c = params.getEntry(2);
            double d = params.getEntry(3);
            double e = params.getEntry(4);

            double[] modelValues  = new double[xData.length];
            double[][] jacobianData = new double[xData.length][5];

            for (int i = 0; i < xData.length; i++) {
                double x = xData[i];

                // u = (x/C)^B,  v = (1 + u)^E
                double u       = Math.pow(x / c, b);
                double onePlusU = 1.0 + u;
                double v       = Math.pow(onePlusU, e);

                // Precompute log terms (guarded against non-positive arguments)
                double lnXoverC   = (x > 0.0 && c > 0.0) ? Math.log(x / c) : 0.0;
                double lnOnePlusU = Math.log(onePlusU);     // onePlusU ≥ 1, always safe

                // y = D + (A − D) / v
                modelValues[i] = d + (a - d) / v;

                // ∂y/∂A = 1 / v
                jacobianData[i][0] = 1.0 / v;

                // ∂y/∂B = −(A−D) · E · u · ln(x/C) / (onePlusU · v)
                jacobianData[i][1] = -(a - d) * e * u * lnXoverC / (onePlusU * v);

                // ∂y/∂C = (A−D) · E · B · u / (C · onePlusU · v)
                jacobianData[i][2] = (a - d) * e * b * u / (c * onePlusU * v);

                // ∂y/∂D = 1 − 1/v
                jacobianData[i][3] = 1.0 - 1.0 / v;

                // ∂y/∂E = −(A−D) · ln(1+u) / v
                jacobianData[i][4] = -(a - d) * lnOnePlusU / v;
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
                .maxEvaluations(maxEvaluations)
                .maxIterations(MAX_ITERATIONS)
                .build();

        LeastSquaresOptimizer.Optimum optimum;
        try {
            optimum = new LevenbergMarquardtOptimizer().optimize(problem);
        } catch (TooManyEvaluationsException e) {
            throw new IllegalStateException(
                    "Curve fitting did not converge after " + maxEvaluations
                    + " evaluations. The calibration data may not fit a 5PL model.", e);
        }

        double rms = optimum.getRMS();
        if (rms > RMS_WARN_THRESHOLD) {
            log.warn("5PL fit converged but RMS={} exceeds threshold {}. "
                    + "Calibration data may not be well described by the 5PL model.", rms, RMS_WARN_THRESHOLD);
        }

        double[] fitted = optimum.getPoint().toArray();

        Map<String, Double> resultParams = new HashMap<>();
        resultParams.put(PARAM_A, fitted[0]);
        resultParams.put(PARAM_B, fitted[1]);
        resultParams.put(PARAM_C, fitted[2]);
        resultParams.put(PARAM_D, fitted[3]);
        resultParams.put(PARAM_E, fitted[4]);
        resultParams.put(CurveParameters.META_CONVERGENCE, 1.0);
        resultParams.put(CurveParameters.META_RMS, rms);
        return new CurveParameters(resultParams);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inverse formula:
     * {@code x = C · (((A − D) / (y − D))^(1/E) − 1)^(1/B)}</p>
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
        double e = params.values().get(PARAM_E);

        double lo = Math.min(a, d);
        double hi = Math.max(a, d);

        if (signal <= lo || signal >= hi) {
            throw new IllegalArgumentException(String.format(
                    "Signal %.6f is outside the interpolable range (%.6f, %.6f). "
                    + "The signal must be strictly between the two asymptotes.",
                    signal, lo, hi));
        }

        // Step 1: ratio = (A − D) / (y − D)  — must be > 0 inside the valid range
        double ratio = (a - d) / (signal - d);

        if (ratio <= 0.0) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation failed: ratio (A−D)/(y−D) = %.6f ≤ 0 for signal %.6f.",
                    ratio, signal));
        }

        // Step 2: innerBase = ratio^(1/E) − 1  — must be > 0
        double innerBase = Math.pow(ratio, 1.0 / e) - 1.0;

        if (innerBase <= 0.0) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation failed: intermediate value %.6f ≤ 0 for signal %.6f. "
                    + "The signal may be at or very close to an asymptote.",
                    innerBase, signal));
        }

        // Step 3: x = C · innerBase^(1/B)
        double result = c * Math.pow(innerBase, 1.0 / b);
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new IllegalArgumentException(String.format(
                    "Back-calculation produced invalid result (NaN/Infinity) for signal %.6f "
                    + "(innerBase=%.6f, B=%.6f, C=%.6f). "
                    + "The signal may be outside the curve's valid range.",
                    signal, innerBase, b, c));
        }
        return result;
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
     *   <li>E₀ = 1.0 — symmetric starting point (reduces model to 4PL)</li>
     * </ul>
     *
     * @param xData array of concentration values
     * @param yData array of signal values
     * @return initial parameter array in order [A, B, C, D, E]
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

        return new double[]{minY, 1.0, initialC, maxY, 1.0};
    }
}

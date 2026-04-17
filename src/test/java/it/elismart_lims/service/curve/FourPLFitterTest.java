package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.util.Pair;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * Parameterized tests for {@link FourPLFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>Calibration points are generated from the known 4PL model
 * {@code y = D + (A - D) / (1 + (x / C)^B)} with:</p>
 * <ul>
 *   <li>A = 0.0 (bottom asymptote)</li>
 *   <li>B = 1.5 (slope)</li>
 *   <li>C = 50.0 (inflection point)</li>
 *   <li>D = 2.0 (top asymptote)</li>
 * </ul>
 *
 * <pre>
 *   x =   5  → y ≈ 0.06130686
 *   x =  10  → y ≈ 0.16419903
 *   x =  25  → y ≈ 0.52240775
 *   x =  50  → y = 1.00000000  (inflection point)
 *   x = 100  → y ≈ 1.47759225
 *   x = 200  → y ≈ 1.77777778
 * </pre>
 */
class FourPLFitterTest {

    /** Acceptance tolerance for recovered parameters: 5 %. */
    private static final double PARAM_TOLERANCE_PCT = 5.0;

    /** Acceptance tolerance for back-interpolated concentration: 5 %. */
    private static final double INTERP_TOLERANCE_PCT = 5.0;

    /** True model parameters used to generate calibration data. */
    private static final double TRUE_A = 0.0;
    private static final double TRUE_B = 1.5;
    private static final double TRUE_C = 50.0;
    private static final double TRUE_D = 2.0;

    /**
     * Six noiseless calibration points generated from the true model above.
     * Values rounded to 8 decimal places match the python3 reference computation.
     */
    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(5.0,   0.06130686),
            new CalibrationPoint(10.0,  0.16419903),
            new CalibrationPoint(25.0,  0.52240775),
            new CalibrationPoint(50.0,  1.00000000),
            new CalibrationPoint(100.0, 1.47759225),
            new CalibrationPoint(200.0, 1.77777778)
    );

    private FourPLFitter fitter;

    /** Creates a fresh {@link FourPLFitter} before each test. */
    @BeforeEach
    void setUp() {
        fitter = new FourPLFitter();
    }

    // -------------------------------------------------------------------------
    // Parameter recovery
    // -------------------------------------------------------------------------

    /**
     * Verifies that each fitted parameter is within {@value PARAM_TOLERANCE_PCT}%
     * of the true value used to generate the noiseless calibration data.
     *
     * <p>A = 0.0 is special — percentage error is undefined when the true value is 0.
     * We use an absolute tolerance of 0.05 (i.e. within 5% of the signal range [0, 2]).</p>
     */
    @Test
    @DisplayName("fit() recovers 4PL parameters within 5% of true values")
    void fit_shouldRecoverParameters() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        double fittedA = params.values().get(FourPLFitter.PARAM_A);
        double fittedB = params.values().get(FourPLFitter.PARAM_B);
        double fittedC = params.values().get(FourPLFitter.PARAM_C);
        double fittedD = params.values().get(FourPLFitter.PARAM_D);

        // A is 0 → use absolute tolerance (0.05 = 2.5% of signal range 0..2)
        assertThat(fittedA).isCloseTo(TRUE_A, offset(0.05));

        assertThat(fittedB).isCloseTo(TRUE_B, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(fittedC).isCloseTo(TRUE_C, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(fittedD).isCloseTo(TRUE_D, withPercentage(PARAM_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Back-interpolation
    // -------------------------------------------------------------------------

    /**
     * Verifies that back-interpolating a signal that was generated at a known
     * concentration recovers that concentration within {@value INTERP_TOLERANCE_PCT}%.
     *
     * <p>At the inflection point (x=50, y=1.0) the inverse is exact by design.</p>
     *
     * @param signal      measured signal (parsed from CSV source)
     * @param expectedConc expected concentration (parsed from CSV source)
     */
    @ParameterizedTest(name = "interpolate(signal={0}) → concentration≈{1}")
    @CsvSource({
            "0.06130686, 5.0",
            "0.16419903, 10.0",
            "0.52240775, 25.0",
            "1.00000000, 50.0",
            "1.47759225, 100.0",
            "1.77777778, 200.0"
    })
    @DisplayName("interpolate() recovers concentration within 5% of true value")
    void interpolate_shouldRecoverConcentration(double signal, double expectedConc) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double interpolated = fitter.interpolate(signal, params);
        assertThat(interpolated).isCloseTo(expectedConc, withPercentage(INTERP_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    /**
     * Signals at or beyond the asymptotes (≤ A or ≥ D) must throw
     * {@link IllegalArgumentException} because the inverse is undefined there.
     *
     * @param signal signal value that lies outside or at the boundary of [A, D]
     */
    @ParameterizedTest(name = "interpolate(signal={0}) throws for out-of-range signal")
    @CsvSource({
            "-0.5",   // clearly below A (≈0)
            "-0.1",   // below A
            "2.5",    // clearly above D (≈2)
            "3.0"     // well above D
    })
    @DisplayName("interpolate() throws IllegalArgumentException for signal outside asymptote range")
    void interpolate_shouldThrowForOutOfRangeSignal(double signal) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(signal, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * A signal exactly equal to the top asymptote D makes the inverse formula blow up:
     * {@code (A − D) / (y − D) − 1 = (A − D) / 0 − 1 = ±Infinity}.
     * The range guard must fire before any arithmetic is attempted.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at upper asymptote D")
    void interpolate_shouldThrowForSignalAtUpperAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double d = params.values().get(FourPLFitter.PARAM_D);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(d, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * A signal exactly equal to the bottom asymptote A (≈ 0) makes the inverse ratio
     * zero or negative, which is outside the valid inversion domain.
     * The range guard must fire before any arithmetic is attempted.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at lower asymptote A")
    void interpolate_shouldThrowForSignalAtLowerAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double a = params.values().get(FourPLFitter.PARAM_A);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(a, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * When one calibration point has {@code concentration = 0}, it must be silently
     * excluded from the fit (the Jacobian is undefined there). The remaining 6 points
     * are enough to converge, and the recovered parameters must still be within 5%
     * of the true values.
     */
    @Test
    @DisplayName("fit() excludes zero-concentration point and still converges")
    void fit_shouldExcludeZeroConcentrationPoint() {
        List<CalibrationPoint> withZero = new java.util.ArrayList<>(REFERENCE_POINTS);
        withZero.add(0, new CalibrationPoint(0.0, 0.0));   // prepend zero-conc point

        CurveParameters params = fitter.fit(withZero);

        assertThat(params.values().get(FourPLFitter.PARAM_A)).isCloseTo(TRUE_A, org.assertj.core.data.Offset.offset(0.05));
        assertThat(params.values().get(FourPLFitter.PARAM_B)).isCloseTo(TRUE_B, org.assertj.core.data.Percentage.withPercentage(5.0));
        assertThat(params.values().get(FourPLFitter.PARAM_C)).isCloseTo(TRUE_C, org.assertj.core.data.Percentage.withPercentage(5.0));
        assertThat(params.values().get(FourPLFitter.PARAM_D)).isCloseTo(TRUE_D, org.assertj.core.data.Percentage.withPercentage(5.0));
    }

    /**
     * Attempting to fit with fewer than 4 points must throw
     * {@link IllegalArgumentException} immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException when fewer than 4 points are supplied")
    void fit_shouldThrowWhenTooFewPoints() {
        List<CalibrationPoint> tooFew = List.of(
                new CalibrationPoint(10.0, 0.5),
                new CalibrationPoint(50.0, 1.0),
                new CalibrationPoint(100.0, 1.5)
        );
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(tooFew))
                .withMessageContaining("at least 4");
    }

    /**
     * Attempting to fit with a {@code null} list must throw
     * {@link IllegalArgumentException}.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException for null point list")
    void fit_shouldThrowForNullPoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(null))
                .withMessageContaining("at least 4");
    }

    // -------------------------------------------------------------------------
    // WLS weight computation
    // -------------------------------------------------------------------------

    /**
     * A calibration point with a non-positive signal (zero, negative) must produce
     * weight 1.0 (fallback), not a division-by-zero, NaN, or negative weight.
     *
     * @param signal a non-positive signal value
     */
    @ParameterizedTest(name = "signal={0} uses fallback weight 1.0")
    @CsvSource({"0.0", "-1.0", "-0.001"})
    @DisplayName("computeWeights: non-positive signal uses fallback weight 1.0")
    void computeWeights_nonPositiveSignal_returnsFallback(double signal) {
        var points = List.of(new CalibrationPoint(1.0, signal));

        double[] weights = CurveFitter.computeWeights(points);

        assertThat(weights).hasSize(1);
        assertThat(weights[0]).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Convergence metadata
    // -------------------------------------------------------------------------

    /**
     * A well-behaved 4PL dataset must produce {@link CurveParameters} containing
     * {@link CurveParameters#META_CONVERGENCE} = 1.0 and a finite, non-negative
     * {@link CurveParameters#META_RMS} value below the {@link FourPLFitter#RMS_WARN_THRESHOLD}.
     */
    @Test
    @DisplayName("fit() on convergent data returns _convergence=1.0 and a reasonable _rms")
    void fit_convergentData_shouldReturnConvergenceMetadata() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values()).containsKey(CurveParameters.META_CONVERGENCE);
        assertThat(params.values()).containsKey(CurveParameters.META_RMS);

        double convergence = params.values().get(CurveParameters.META_CONVERGENCE);
        double rms        = params.values().get(CurveParameters.META_RMS);

        assertThat(convergence).isEqualTo(1.0);
        assertThat(rms).isFinite().isNotNegative().isLessThanOrEqualTo(FourPLFitter.RMS_WARN_THRESHOLD);
    }

    /**
     * Verifies that fit() populates _r2, _rmse, and _df for noiseless data.
     * Data is generated from the exact 4PL formula so R² must be ≥ 0.999.
     */
    @Test
    @DisplayName("fit() populates _r2, _rmse, _df goodness-of-fit metrics")
    void fit_shouldPopulateGoodnessOfFitMetrics() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values()).containsKey(CurveParameters.META_R2);
        assertThat(params.values()).containsKey(CurveParameters.META_RMSE);
        assertThat(params.values()).containsKey(CurveParameters.META_DF);
        assertThat(params.values()).containsKey(CurveParameters.META_RMS);   // unchanged

        // Noiseless data from exact formula → near-perfect fit
        assertThat(params.values().get(CurveParameters.META_R2)).isGreaterThan(0.999);
        assertThat(params.values().get(CurveParameters.META_RMSE)).isLessThan(0.01);
        // df = 6 points - 4 parameters = 2
        assertThat(params.values().get(CurveParameters.META_DF)).isCloseTo(2.0, offset(1e-10));
    }

    /**
     * When the LM optimizer is given only 1 evaluation it must fail immediately.
     * The fitter must catch {@code TooManyEvaluationsException} and rethrow it as
     * {@link IllegalStateException} with a message that mentions non-convergence.
     *
     * <p>The package-private {@code FourPLFitter(int maxEvaluations)} constructor is used
     * here solely to keep the test fast; in production {@value FourPLFitter#DEFAULT_MAX_EVALUATIONS}
     * evaluations are allowed.</p>
     */
    @Test
    @DisplayName("fit() throws IllegalStateException when optimizer hits evaluation limit")
    void fit_evaluationLimitExceeded_shouldThrowIllegalStateException() {
        FourPLFitter limitedFitter = new FourPLFitter(1); // 1 evaluation → immediate failure
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> limitedFitter.fit(REFERENCE_POINTS))
                .withMessageContaining("did not converge");
    }

    // -------------------------------------------------------------------------
    // WLS vs OLS on heteroscedastic data
    // -------------------------------------------------------------------------

    /**
     * Heteroscedastic calibration data: true model A=0, B=1.5, C=50, D=2.
     * The two high-signal calibrators (x=100, x=200) have a +20% signal offset
     * simulating proportional noise. OLS is dominated by these large-residual
     * high-signal points and biases EC50 upward. WLS (1/y²) down-weights them
     * and recovers EC50 closer to the true value.
     *
     * <p>Reference signals (noiseless from REFERENCE_POINTS):
     * x=100 → 1.47759225 × 1.20 = 1.77311070
     * x=200 → 1.77777778 × 1.20 = 2.13333334</p>
     */
    private static final List<CalibrationPoint> HETEROSCEDASTIC_POINTS = List.of(
            new CalibrationPoint(5.0,    0.06130686),
            new CalibrationPoint(10.0,   0.16419903),
            new CalibrationPoint(25.0,   0.52240775),
            new CalibrationPoint(50.0,   1.00000000),
            new CalibrationPoint(100.0,  1.77311070),
            new CalibrationPoint(200.0,  2.13333334)
    );

    @Test
    @DisplayName("WLS: wide-range heteroscedastic data — EC50 differs from OLS by >= 1%")
    void fit_wideRangeHeteroscedastic_wlsEC50MoreAccurateThanOls() {
        double trueC = 50.0;

        CurveParameters wlsParams = fitter.fit(HETEROSCEDASTIC_POINTS);
        double wlsC = wlsParams.values().get(FourPLFitter.PARAM_C);

        CurveParameters olsParams = fitWithUnitWeights(HETEROSCEDASTIC_POINTS);
        double olsC = olsParams.values().get(FourPLFitter.PARAM_C);

        // The two estimates must differ by at least 1% of the true EC50,
        // demonstrating that WLS weighting has a measurable effect on heteroscedastic data
        assertThat(Math.abs(wlsC - olsC) / trueC * 100)
                .as("WLS and OLS EC50 must differ by >= 1%% on heteroscedastic data "
                        + "(WLS_C=%.3f, OLS_C=%.3f)", wlsC, olsC)
                .isGreaterThan(1.0);
    }

    /**
     * Narrow-range noiseless calibration data: true model A=0, B=1.5, C=50, D=2.
     * All signals lie in [0.52, 1.29], a ~6x weight spread. WLS and OLS should
     * agree to within 2% on EC50 because the weighting has little leverage.
     *
     * <p>Signal values computed from the true model at x = 25, 35, 45, 55, 65, 75.</p>
     */
    private static final List<CalibrationPoint> NARROW_RANGE_POINTS = List.of(
            new CalibrationPoint(25.0, 0.52240775),
            new CalibrationPoint(35.0, 0.73854000),
            new CalibrationPoint(45.0, 0.92120000),
            new CalibrationPoint(55.0, 1.07140000),
            new CalibrationPoint(65.0, 1.19430000),
            new CalibrationPoint(75.0, 1.29490000)
    );

    @Test
    @DisplayName("WLS: narrow-range data — EC50 within 5%% of true and agrees with OLS within 2%%")
    void fit_narrowRange_wlsAndOlsAgree() {
        double trueC = 50.0;

        CurveParameters wlsParams = fitter.fit(NARROW_RANGE_POINTS);
        double wlsC = wlsParams.values().get(FourPLFitter.PARAM_C);

        CurveParameters olsParams = fitWithUnitWeights(NARROW_RANGE_POINTS);
        double olsC = olsParams.values().get(FourPLFitter.PARAM_C);

        // Both methods recover the true EC50 within 5%
        assertThat(wlsC).as("WLS EC50").isCloseTo(trueC, withPercentage(5.0));
        assertThat(olsC).as("OLS EC50").isCloseTo(trueC, withPercentage(5.0));

        // The two estimates agree with each other within 2%
        assertThat(Math.abs(wlsC - olsC) / trueC * 100)
                .as("WLS and OLS EC50 must agree within 2%% on narrow-range data")
                .isLessThan(2.0);
    }

    // -------------------------------------------------------------------------
    // Data-driven B₀ initial guess
    // -------------------------------------------------------------------------

    /**
     * Verifies that starting the LM optimizer from the data-driven B₀ estimate
     * requires no more iterations than starting from the hardcoded B₀ = 1.0.
     *
     * <p>Reference data: true B = 1.5. {@code estimateHillSlope} returns ≈ 1.83,
     * which is closer to the true value than 1.0 and should not increase iteration count.
     * The assertion uses {@code ≤} (soft): equal counts are acceptable.</p>
     */
    @Test
    @DisplayName("estimateHillSlope: data-driven B₀ needs no more LM iterations than B₀=1.0")
    void fit_dataDrivenB0_needsNoMoreIterationsThanFixedB0() {
        double[] xData = REFERENCE_POINTS.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = REFERENCE_POINTS.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double minY = DoubleStream.of(yData).min().orElse(0.0);
        double maxY = DoubleStream.of(yData).max().orElse(2.0);
        double minX = DoubleStream.of(xData).min().orElse(1.0);
        double maxX = DoubleStream.of(xData).max().orElse(100.0);
        double c0 = Math.sqrt(minX * maxX);

        double estimatedB0 = CurveFitter.estimateHillSlope(xData, yData);

        double[] startEstimated = {minY, estimatedB0, c0, maxY};
        double[] startFixed     = {minY, 1.0,          c0, maxY};

        int iterEstimated = count4PLIterations(xData, yData, startEstimated);
        int iterFixed     = count4PLIterations(xData, yData, startFixed);

        assertThat(iterEstimated)
                .as("Data-driven B₀=%.3f should need no more LM iterations than fixed B₀=1.0 "
                                + "(estimated=%d, fixed=%d)", estimatedB0, iterEstimated, iterFixed)
                .isLessThanOrEqualTo(iterFixed);
    }

    /**
     * Runs the 4PL LM optimizer from the given {@code start} vector and returns
     * the iteration count. Used only to compare convergence speed of different
     * starting guesses — not part of the production fitting path.
     */
    private static int count4PLIterations(double[] xData, double[] yData, double[] start) {
        MultivariateJacobianFunction model = params -> {
            double a = params.getEntry(0), b = params.getEntry(1);
            double c = params.getEntry(2), d = params.getEntry(3);
            double[] vals = new double[xData.length];
            double[][] jac  = new double[xData.length][4];
            for (int i = 0; i < xData.length; i++) {
                double x     = xData[i];
                double ratio = Math.pow(x / c, b);
                double denom = 1.0 + ratio;
                double den2  = denom * denom;
                vals[i]   = d + (a - d) / denom;
                jac[i][0] = 1.0 / denom;
                double lnR = (x > 0 && c > 0) ? Math.log(x / c) : 0.0;
                jac[i][1] = -(a - d) * ratio * lnR / den2;
                jac[i][2] = (a - d) * b * ratio / (c * den2);
                jac[i][3] = 1.0 - 1.0 / denom;
            }
            return Pair.create(new ArrayRealVector(vals, false),
                    new Array2DRowRealMatrix(jac, false));
        };

        // Use unit weights to isolate the effect of B₀ (not WLS weight differences)
        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(start).model(model).target(yData)
                .lazyEvaluation(false).maxEvaluations(10_000).maxIterations(10_000)
                .build();

        LeastSquaresOptimizer.Optimum opt = new LevenbergMarquardtOptimizer().optimize(problem);
        return opt.getIterations();
    }

    /**
     * Fits a 4PL curve using OLS (unit weights) on the given points.
     * Used only in tests to compare WLS vs OLS behaviour.
     * Replicates the FourPLFitter optimisation loop without calling
     * CurveFitter.computeWeights — equivalent to an identity weight matrix.
     */
    private static CurveParameters fitWithUnitWeights(List<CalibrationPoint> points) {
        double[] xData = points.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = points.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double minY = DoubleStream.of(yData).min().orElse(0.0);
        double maxY = DoubleStream.of(yData).max().orElse(2.0);
        double minX = DoubleStream.of(xData).min().orElse(1.0);
        double maxX = DoubleStream.of(xData).max().orElse(100.0);
        double[] start = {minY, 1.0, Math.sqrt(minX * maxX), maxY};

        MultivariateJacobianFunction model = params -> {
            double a = params.getEntry(0), b = params.getEntry(1);
            double c = params.getEntry(2), d = params.getEntry(3);
            double[] vals = new double[xData.length];
            double[][] jac  = new double[xData.length][4];
            for (int i = 0; i < xData.length; i++) {
                double x      = xData[i];
                double ratio  = Math.pow(x / c, b);
                double denom  = 1.0 + ratio;
                double denom2 = denom * denom;
                vals[i]  = d + (a - d) / denom;
                jac[i][0] = 1.0 / denom;
                double lnR = (x > 0 && c > 0) ? Math.log(x / c) : 0.0;
                jac[i][1] = -(a - d) * ratio * lnR / denom2;
                jac[i][2] = (a - d) * b * ratio / (c * denom2);
                jac[i][3] = 1.0 - 1.0 / denom;
            }
            return Pair.create(new ArrayRealVector(vals, false),
                               new Array2DRowRealMatrix(jac, false));
        };

        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(start).model(model).target(yData)
                .lazyEvaluation(false).maxEvaluations(10_000).maxIterations(10_000)
                .build(); // no .weight() call → OLS

        LeastSquaresOptimizer.Optimum opt = new LevenbergMarquardtOptimizer().optimize(problem);
        double[] p = opt.getPoint().toArray();
        return new CurveParameters(Map.of("A", p[0], "B", p[1], "C", p[2], "D", p[3]));
    }
}

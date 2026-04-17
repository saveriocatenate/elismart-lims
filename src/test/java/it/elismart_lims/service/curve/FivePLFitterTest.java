package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
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
 * Parameterized tests for {@link FivePLFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>Calibration points are generated from the known 5PL model
 * {@code y = D + (A - D) / (1 + (x / C)^B)^E} with:</p>
 * <ul>
 *   <li>A = 0.0 (bottom asymptote)</li>
 *   <li>B = 2.0 (slope)</li>
 *   <li>C = 100.0 (inflection point)</li>
 *   <li>D = 3.0 (top asymptote)</li>
 *   <li>E = 0.5 (asymmetry; simplifies model to {@code y = D + (A-D) / sqrt(1 + (x/C)^B)})</li>
 * </ul>
 *
 * <p>E = 0.5 was chosen deliberately (not 1.0) to exercise the asymmetric path and to
 * produce exact square-root expressions at round concentrations:</p>
 * <pre>
 *   x =   10  → v = sqrt(1.01)       → y ≈ 0.01487123
 *   x =   50  → v = sqrt(1.25)       → y ≈ 0.31671843
 *   x =  100  → v = sqrt(2)          → y ≈ 0.87867966
 *   x =  200  → v = sqrt(5)          → y ≈ 1.65835921
 *   x =  500  → v = sqrt(26)         → y ≈ 2.41168447
 *   x = 1000  → v = sqrt(101)        → y ≈ 2.70148766
 * </pre>
 */
class FivePLFitterTest {

    /** Acceptance tolerance for recovered parameters: 5 %. */
    private static final double PARAM_TOLERANCE_PCT = 5.0;

    /** Acceptance tolerance for back-interpolated concentration: 5 %. */
    private static final double INTERP_TOLERANCE_PCT = 5.0;

    /** True model parameters used to generate calibration data. */
    private static final double TRUE_A = 0.0;
    private static final double TRUE_B = 2.0;
    private static final double TRUE_C = 100.0;
    private static final double TRUE_D = 3.0;
    private static final double TRUE_E = 0.5;

    /**
     * Six noiseless calibration points generated from the true model above.
     * Computed as {@code y = 3 - 3 / sqrt(1 + (x/100)^2)}.
     */
    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(10.0,    0.01487123),
            new CalibrationPoint(50.0,    0.31671843),
            new CalibrationPoint(100.0,   0.87867966),
            new CalibrationPoint(200.0,   1.65835921),
            new CalibrationPoint(500.0,   2.41168447),
            new CalibrationPoint(1000.0,  2.70148766)
    );

    private FivePLFitter fitter;

    /** Creates a fresh {@link FivePLFitter} before each test. */
    @BeforeEach
    void setUp() {
        fitter = new FivePLFitter();
    }

    // -------------------------------------------------------------------------
    // Parameter recovery
    // -------------------------------------------------------------------------

    /**
     * Verifies that each fitted parameter is within {@value PARAM_TOLERANCE_PCT}%
     * of the true value used to generate the noiseless calibration data.
     *
     * <p>A = 0.0 is special — percentage error is undefined when the true value is 0.
     * We use an absolute tolerance of 0.15 (5% of the signal range [0, 3]).</p>
     */
    @Test
    @DisplayName("fit() recovers 5PL parameters within 5% of true values")
    void fit_shouldRecoverParameters() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        double fittedA = params.values().get(FivePLFitter.PARAM_A);
        double fittedB = params.values().get(FivePLFitter.PARAM_B);
        double fittedC = params.values().get(FivePLFitter.PARAM_C);
        double fittedD = params.values().get(FivePLFitter.PARAM_D);
        double fittedE = params.values().get(FivePLFitter.PARAM_E);

        // A is 0 → use absolute tolerance (0.15 = 5% of signal range 0..3)
        assertThat(fittedA).isCloseTo(TRUE_A, offset(0.15));

        assertThat(fittedB).isCloseTo(TRUE_B, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(fittedC).isCloseTo(TRUE_C, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(fittedD).isCloseTo(TRUE_D, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(fittedE).isCloseTo(TRUE_E, withPercentage(PARAM_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Back-interpolation
    // -------------------------------------------------------------------------

    /**
     * Verifies that back-interpolating a signal that was generated at a known
     * concentration recovers that concentration within {@value INTERP_TOLERANCE_PCT}%.
     *
     * @param signal       measured signal (parsed from CSV source)
     * @param expectedConc expected concentration (parsed from CSV source)
     */
    @ParameterizedTest(name = "interpolate(signal={0}) → concentration≈{1}")
    @CsvSource({
            "0.01487123,  10.0",
            "0.31671843,  50.0",
            "0.87867966, 100.0",
            "1.65835921, 200.0",
            "2.41168447, 500.0",
            "2.70148766, 1000.0"
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
            "3.5",    // clearly above D (≈3)
            "4.0"     // well above D
    })
    @DisplayName("interpolate() throws IllegalArgumentException for signal outside asymptote range")
    void interpolate_shouldThrowForOutOfRangeSignal(double signal) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(signal, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * A signal exactly equal to the top asymptote D makes (A − D)/(y − D) = −Infinity.
     * The range guard must fire before any arithmetic is attempted.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at upper asymptote D")
    void interpolate_shouldThrowForSignalAtUpperAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double d = params.values().get(FivePLFitter.PARAM_D);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(d, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * A signal exactly equal to the bottom asymptote A (≈ 0) is at the boundary of
     * the inversion domain. The range guard must fire before any arithmetic.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at lower asymptote A")
    void interpolate_shouldThrowForSignalAtLowerAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double a = params.values().get(FivePLFitter.PARAM_A);
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

        assertThat(params.values().get(FivePLFitter.PARAM_A)).isCloseTo(TRUE_A, org.assertj.core.data.Offset.offset(0.15));
        assertThat(params.values().get(FivePLFitter.PARAM_B)).isCloseTo(TRUE_B, org.assertj.core.data.Percentage.withPercentage(5.0));
        assertThat(params.values().get(FivePLFitter.PARAM_C)).isCloseTo(TRUE_C, org.assertj.core.data.Percentage.withPercentage(5.0));
        assertThat(params.values().get(FivePLFitter.PARAM_D)).isCloseTo(TRUE_D, org.assertj.core.data.Percentage.withPercentage(5.0));
        assertThat(params.values().get(FivePLFitter.PARAM_E)).isCloseTo(TRUE_E, org.assertj.core.data.Percentage.withPercentage(5.0));
    }

    /**
     * Attempting to fit with fewer than 5 points must throw
     * {@link IllegalArgumentException} immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException when fewer than 5 points are supplied")
    void fit_shouldThrowWhenTooFewPoints() {
        List<CalibrationPoint> tooFew = List.of(
                new CalibrationPoint(10.0,  0.01487123),
                new CalibrationPoint(50.0,  0.31671843),
                new CalibrationPoint(100.0, 0.87867966),
                new CalibrationPoint(200.0, 1.65835921)
        );
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(tooFew))
                .withMessageContaining("at least 5");
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
                .withMessageContaining("at least 5");
    }

    // -------------------------------------------------------------------------
    // Convergence metadata
    // -------------------------------------------------------------------------

    /**
     * A well-behaved 5PL dataset must produce {@link CurveParameters} containing
     * {@link CurveParameters#META_CONVERGENCE} = 1.0 and a finite, non-negative
     * {@link CurveParameters#META_RMS} value below the {@link FivePLFitter#RMS_WARN_THRESHOLD}.
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
        assertThat(rms).isFinite().isNotNegative().isLessThanOrEqualTo(FivePLFitter.RMS_WARN_THRESHOLD);
    }

    /**
     * When the LM optimizer is given only 1 evaluation it must fail immediately.
     * The fitter must catch {@code TooManyEvaluationsException} and rethrow it as
     * {@link IllegalStateException} with a message that mentions non-convergence.
     */
    @Test
    @DisplayName("fit() throws IllegalStateException when optimizer hits evaluation limit")
    void fit_evaluationLimitExceeded_shouldThrowIllegalStateException() {
        FivePLFitter limitedFitter = new FivePLFitter(1);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> limitedFitter.fit(REFERENCE_POINTS))
                .withMessageContaining("did not converge");
    }

    // -------------------------------------------------------------------------
    // Goodness-of-fit metrics
    // -------------------------------------------------------------------------

    /**
     * Verifies that fit() populates R², RMSE, and df goodness-of-fit metrics
     * in the returned CurveParameters.
     */
    @Test
    @DisplayName("fit() populates _r2, _rmse, _df goodness-of-fit metrics")
    void fit_shouldPopulateGoodnessOfFitMetrics() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values()).containsKey(CurveParameters.META_R2);
        assertThat(params.values()).containsKey(CurveParameters.META_RMSE);
        assertThat(params.values()).containsKey(CurveParameters.META_DF);
        assertThat(params.values()).containsKey(CurveParameters.META_RMS);

        assertThat(params.values().get(CurveParameters.META_R2)).isGreaterThan(0.999);
        assertThat(params.values().get(CurveParameters.META_RMSE)).isLessThan(0.01);
        assertThat(params.values().get(CurveParameters.META_DF)).isGreaterThanOrEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Data-driven B₀ initial guess
    // -------------------------------------------------------------------------

    /**
     * Verifies that starting the 5PL LM optimizer from the data-driven B₀ estimate
     * requires no more iterations than starting from the hardcoded B₀ = 1.0.
     *
     * <p>Reference data: true B = 2.0, true E = 0.5. {@code estimateHillSlope}
     * returns ≈ 1.78 — closer to the true B than 1.0. The assertion uses {@code ≤}
     * (soft): equal counts are acceptable.</p>
     */
    @Test
    @DisplayName("estimateHillSlope: data-driven B₀ needs no more LM iterations than B₀=1.0 (5PL)")
    void fit_dataDrivenB0_needsNoMoreIterationsThanFixedB0() {
        double[] xData = REFERENCE_POINTS.stream().mapToDouble(CalibrationPoint::concentration).toArray();
        double[] yData = REFERENCE_POINTS.stream().mapToDouble(CalibrationPoint::signal).toArray();

        double minY = DoubleStream.of(yData).min().orElse(0.0);
        double maxY = DoubleStream.of(yData).max().orElse(3.0);
        double minX = DoubleStream.of(xData).min().orElse(1.0);
        double maxX = DoubleStream.of(xData).max().orElse(1000.0);
        double c0 = Math.sqrt(minX * maxX);

        double estimatedB0 = CurveFitter.estimateHillSlope(xData, yData);

        // [A, B, C, D, E] — E₀ stays 1.0 in both cases
        double[] startEstimated = {minY, estimatedB0, c0, maxY, 1.0};
        double[] startFixed     = {minY, 1.0,          c0, maxY, 1.0};

        int iterEstimated = count5PLIterations(xData, yData, startEstimated);
        int iterFixed     = count5PLIterations(xData, yData, startFixed);

        // isLessThanOrEqualTo (soft): equal counts are acceptable. Note: this asserts an
        // optimizer-internal quantity (LM iterations) — if the test becomes flaky after a
        // Commons Math upgrade, the iteration counts may have shifted; re-evaluate then.
        assertThat(iterEstimated)
                .as("Data-driven B₀=%.3f should need no more LM iterations than fixed B₀=1.0 "
                                + "(estimated=%d, fixed=%d)", estimatedB0, iterEstimated, iterFixed)
                .isLessThanOrEqualTo(iterFixed);
    }

    /**
     * Runs the 5PL LM optimizer from the given {@code start} vector [A,B,C,D,E]
     * and returns the iteration count. Uses unit weights (OLS) to isolate the effect
     * of B₀ from WLS weight differences.
     *
     * @param xData concentration values
     * @param yData signal values
     * @param start initial parameter vector [A, B, C, D, E]
     * @return number of LM optimizer iterations to convergence
     */
    private static int count5PLIterations(double[] xData, double[] yData, double[] start) {
        MultivariateJacobianFunction model = params -> {
            double a = params.getEntry(0), b = params.getEntry(1);
            double c = params.getEntry(2), d = params.getEntry(3);
            double e = params.getEntry(4);
            double[] vals = new double[xData.length];
            double[][] jac = new double[xData.length][5];
            for (int i = 0; i < xData.length; i++) {
                double x        = xData[i];
                double u        = Math.pow(x / c, b);
                double onePlusU = 1.0 + u;
                double v        = Math.pow(onePlusU, e);
                double lnXoverC   = (x > 0.0 && c > 0.0) ? Math.log(x / c) : 0.0;
                double lnOnePlusU = Math.log(onePlusU);
                vals[i]   = d + (a - d) / v;
                jac[i][0] = 1.0 / v;
                jac[i][1] = -(a - d) * e * u * lnXoverC / (onePlusU * v);
                jac[i][2] = (a - d) * e * b * u / (c * onePlusU * v);
                jac[i][3] = 1.0 - 1.0 / v;
                jac[i][4] = -(a - d) * lnOnePlusU / v;
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
}

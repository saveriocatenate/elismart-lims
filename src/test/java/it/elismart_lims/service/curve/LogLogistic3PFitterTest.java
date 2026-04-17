package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * Parameterized tests for {@link LogLogistic3PFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>The 3PL model is {@code y = D · (x/C)^B / (1 + (x/C)^B)}, which is equivalent
 * to a 4PL curve with A fixed at zero. Reference points are generated with:</p>
 * <ul>
 *   <li><b>B</b> = 1.5 (slope)</li>
 *   <li><b>C</b> = 50.0 (inflection point)</li>
 *   <li><b>D</b> = 2.0 (top asymptote)</li>
 * </ul>
 *
 * <pre>
 *   x =   5  → y ≈ 0.061328
 *   x =  10  → y ≈ 0.164199
 *   x =  25  → y ≈ 0.522408
 *   x =  50  → y = 1.000000  (inflection point)
 *   x = 100  → y ≈ 1.477592
 *   x = 200  → y ≈ 1.777778
 * </pre>
 */
class LogLogistic3PFitterTest {

    /** Acceptance tolerance for recovered parameters: 5 %. */
    private static final double PARAM_TOLERANCE_PCT = 5.0;

    /** Acceptance tolerance for back-interpolated concentration: 5 %. */
    private static final double INTERP_TOLERANCE_PCT = 5.0;

    /** True model parameters used to generate calibration data. */
    private static final double TRUE_B = 1.5;
    private static final double TRUE_C = 50.0;
    private static final double TRUE_D = 2.0;

    /**
     * Six noiseless calibration points generated from the true model above.
     * Values were verified with Python: {@code y = 2 * (x/50)**1.5 / (1 + (x/50)**1.5)}.
     */
    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(5.0,   0.061328),
            new CalibrationPoint(10.0,  0.164199),
            new CalibrationPoint(25.0,  0.522408),
            new CalibrationPoint(50.0,  1.000000),
            new CalibrationPoint(100.0, 1.477592),
            new CalibrationPoint(200.0, 1.777778)
    );

    private LogLogistic3PFitter fitter;

    /** Creates a fresh {@link LogLogistic3PFitter} before each test. */
    @BeforeEach
    void setUp() {
        fitter = new LogLogistic3PFitter();
    }

    // -------------------------------------------------------------------------
    // Parameter recovery
    // -------------------------------------------------------------------------

    /**
     * Verifies that each fitted parameter is within {@value PARAM_TOLERANCE_PCT}%
     * of the true value used to generate the noiseless calibration data.
     */
    @Test
    @DisplayName("fit() recovers 3PL parameters within 5% of true values")
    void fit_shouldRecoverParameters() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values().get(LogLogistic3PFitter.PARAM_B))
                .isCloseTo(TRUE_B, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(params.values().get(LogLogistic3PFitter.PARAM_C))
                .isCloseTo(TRUE_C, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(params.values().get(LogLogistic3PFitter.PARAM_D))
                .isCloseTo(TRUE_D, withPercentage(PARAM_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Back-interpolation — valid range
    // -------------------------------------------------------------------------

    /**
     * Verifies that back-interpolating a signal that was generated at a known
     * concentration recovers that concentration within {@value INTERP_TOLERANCE_PCT}%.
     *
     * @param signal       measured signal
     * @param expectedConc expected concentration
     */
    @ParameterizedTest(name = "interpolate(signal={0}) → concentration≈{1}")
    @CsvSource({
            "0.061328, 5.0",
            "0.164199, 10.0",
            "0.522408, 25.0",
            "1.000000, 50.0",
            "1.477592, 100.0",
            "1.777778, 200.0"
    })
    @DisplayName("interpolate() recovers concentration within 5% of true value")
    void interpolate_shouldRecoverConcentration(double signal, double expectedConc) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double interpolated = fitter.interpolate(signal, params);
        assertThat(interpolated).isCloseTo(expectedConc, withPercentage(INTERP_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Asymptote boundary tests
    // -------------------------------------------------------------------------

    /**
     * A signal exactly equal to the top asymptote D makes the denominator of the
     * inverse formula {@code (D − y) = 0}. The range guard must fire.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at upper asymptote D")
    void interpolate_shouldThrowForSignalAtUpperAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double d = params.values().get(LogLogistic3PFitter.PARAM_D);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(d, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * A signal of exactly zero equals the lower asymptote (A = 0 in 3PL).
     * The range guard must fire before any division is attempted.
     */
    @Test
    @DisplayName("interpolate() throws for signal exactly at lower asymptote (0)")
    void interpolate_shouldThrowForSignalAtLowerAsymptote() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(0.0, params))
                .withMessageContaining("outside the interpolable range");
    }

    /**
     * Signals clearly outside the valid range must also throw.
     *
     * @param signal signal outside the (0, D) range
     */
    @ParameterizedTest(name = "interpolate(signal={0}) throws for out-of-range signal")
    @CsvSource({
            "-0.5",  // below zero asymptote
            "2.5",   // above top asymptote D≈2
            "3.0"    // well above D
    })
    @DisplayName("interpolate() throws for signal outside asymptote range")
    void interpolate_shouldThrowForOutOfRangeSignal(double signal) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(signal, params))
                .withMessageContaining("outside the interpolable range");
    }

    // -------------------------------------------------------------------------
    // Zero-concentration filtering
    // -------------------------------------------------------------------------

    /**
     * When one calibration point has {@code concentration = 0}, it must be silently
     * excluded from the fit (the Jacobian is undefined at x = 0). The remaining
     * 6 points are sufficient to converge, and parameters must be within 5% of the
     * true values.
     */
    @Test
    @DisplayName("fit() excludes zero-concentration point and still converges")
    void fit_shouldExcludeZeroConcentrationPoint() {
        List<CalibrationPoint> withZero = new ArrayList<>(REFERENCE_POINTS);
        withZero.add(0, new CalibrationPoint(0.0, 0.0));   // prepend zero-conc point

        CurveParameters params = fitter.fit(withZero);

        assertThat(params.values().get(LogLogistic3PFitter.PARAM_B))
                .isCloseTo(TRUE_B, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(params.values().get(LogLogistic3PFitter.PARAM_C))
                .isCloseTo(TRUE_C, withPercentage(PARAM_TOLERANCE_PCT));
        assertThat(params.values().get(LogLogistic3PFitter.PARAM_D))
                .isCloseTo(TRUE_D, withPercentage(PARAM_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // Minimum-points validation
    // -------------------------------------------------------------------------

    /**
     * Attempting to fit with fewer than 3 points must throw immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException when fewer than 3 points are supplied")
    void fit_shouldThrowWhenTooFewPoints() {
        List<CalibrationPoint> tooFew = List.of(
                new CalibrationPoint(10.0, 0.164199),
                new CalibrationPoint(50.0, 1.000000)
        );
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(tooFew))
                .withMessageContaining("at least 3");
    }

    /**
     * Attempting to fit with a {@code null} list must throw immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException for null point list")
    void fit_shouldThrowForNullPoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(null))
                .withMessageContaining("at least 3");
    }

    // -------------------------------------------------------------------------
    // Convergence metadata
    // -------------------------------------------------------------------------

    /**
     * A well-behaved 3PL dataset must produce {@link CurveParameters} containing
     * {@link CurveParameters#META_CONVERGENCE} = 1.0 and a finite, non-negative
     * {@link CurveParameters#META_RMS} value below the {@link LogLogistic3PFitter#RMS_WARN_THRESHOLD}.
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
        assertThat(rms).isFinite().isNotNegative().isLessThanOrEqualTo(LogLogistic3PFitter.RMS_WARN_THRESHOLD);
    }

    /**
     * When the LM optimizer is given only 1 evaluation it must fail immediately.
     * The fitter must catch {@code TooManyEvaluationsException} and rethrow it as
     * {@link IllegalStateException} with a message that mentions non-convergence.
     */
    @Test
    @DisplayName("fit() throws IllegalStateException when optimizer hits evaluation limit")
    void fit_evaluationLimitExceeded_shouldThrowIllegalStateException() {
        LogLogistic3PFitter limitedFitter = new LogLogistic3PFitter(1);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> limitedFitter.fit(REFERENCE_POINTS))
                .withMessageContaining("did not converge");
    }

    // -------------------------------------------------------------------------
    // NaN/Infinity guard (via manual params construction)
    // -------------------------------------------------------------------------

    /**
     * If the fitted B parameter is so extreme that {@code Math.pow} overflows to
     * Infinity, {@link LogLogistic3PFitter#interpolate} must throw rather than
     * return an invalid result.
     *
     * <p>Constructed params: B = 1e300 (huge exponent), C = 50, D = 2.
     * For any signal in (0, 2), {@code (signal/(D-signal))^(1/B) = ...^(~0)} → 1.0,
     * and then {@code C * 1.0 = 50} — actually this won't overflow. Instead use
     * B → 0 so 1/B → Infinity: {@code pow(ratio, Inf) = Inf} when ratio > 1.</p>
     *
     * <p>With B = 1e-300: {@code 1/B = 1e300} and {@code ratio = signal/(D-signal)}.
     * When signal = 1.5, D = 2 → ratio = 3.0 > 1 →
     * {@code Math.pow(3.0, 1e300) = Infinity} → guard fires.</p>
     */
    @Test
    @DisplayName("interpolate() throws when Math.pow produces Infinity (B ≈ 0)")
    void interpolate_shouldThrowWhenResultIsInfinity() {
        // Manually constructed degenerate params: B nearly zero causes 1/B → Infinity
        CurveParameters degenerateParams = new CurveParameters(Map.of(
                LogLogistic3PFitter.PARAM_B, 1e-300,
                LogLogistic3PFitter.PARAM_C, 50.0,
                LogLogistic3PFitter.PARAM_D, 2.0
        ));
        // signal=1.5 is in (0, 2), ratio = 1.5/0.5 = 3.0 > 1, pow(3.0, 1e300) = Infinity
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(1.5, degenerateParams))
                .withMessageContaining("NaN/Infinity");
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
}

package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * Parameterized tests for {@link SemiLogLinearFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>Calibration points are generated from the known semi-log model
 * {@code y = m·ln(x) + q} with:</p>
 * <ul>
 *   <li><b>m</b> = 1.0 (slope on the ln-concentration axis)</li>
 *   <li><b>q</b> = 0.0 (intercept)</li>
 * </ul>
 *
 * <p>This simplifies to {@code y = ln(x)}, whose inverse is {@code x = exp(y)}.</p>
 *
 * <pre>
 *   x =    1  → y = ln(1)    =  0.000000
 *   x =   10  → y = ln(10)   ≈  2.302585
 *   x =  100  → y = ln(100)  ≈  4.605170
 *   x = 1000  → y = ln(1000) ≈  6.907755
 * </pre>
 *
 * <p>The semi-log model has no finite asymptotes; the NaN/Infinity guard becomes
 * relevant when the slope m is extremely small (degenerate fit) and the exponent
 * {@code (y − q) / m} overflows {@link Math#exp}.</p>
 */
class SemiLogLinearFitterTest {

    /** Acceptance tolerance for recovered parameters: 1 % (linear model, should be exact). */
    private static final double PARAM_TOLERANCE_PCT = 1.0;

    /** Acceptance tolerance for back-interpolated concentration: 1 %. */
    private static final double INTERP_TOLERANCE_PCT = 1.0;

    /** True model parameters. */
    private static final double TRUE_M = 1.0;
    private static final double TRUE_Q = 0.0;

    /**
     * Four noiseless calibration points generated from {@code y = ln(x)}.
     */
    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(1.0,    0.000000),
            new CalibrationPoint(10.0,   2.302585),
            new CalibrationPoint(100.0,  4.605170),
            new CalibrationPoint(1000.0, 6.907755)
    );

    private SemiLogLinearFitter fitter;

    /** Creates a fresh {@link SemiLogLinearFitter} before each test. */
    @BeforeEach
    void setUp() {
        fitter = new SemiLogLinearFitter();
    }

    // -------------------------------------------------------------------------
    // Parameter recovery
    // -------------------------------------------------------------------------

    /**
     * Verifies that the fitted slope m and intercept q are within
     * {@value PARAM_TOLERANCE_PCT}% of the true values (q=0 uses absolute tolerance).
     */
    @Test
    @DisplayName("fit() recovers semi-log parameters within 1% of true values")
    void fit_shouldRecoverParameters() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values().get(SemiLogLinearFitter.PARAM_M))
                .isCloseTo(TRUE_M, withPercentage(PARAM_TOLERANCE_PCT));
        // q is 0.0 — use absolute tolerance instead of percentage
        assertThat(params.values().get(SemiLogLinearFitter.PARAM_Q))
                .isCloseTo(TRUE_Q, org.assertj.core.data.Offset.offset(0.01));
    }

    // -------------------------------------------------------------------------
    // Back-interpolation — valid range
    // -------------------------------------------------------------------------

    /**
     * Verifies that back-interpolating a signal produced by the reference model
     * recovers the original concentration within {@value INTERP_TOLERANCE_PCT}%.
     *
     * @param signal       measured signal
     * @param expectedConc expected concentration
     */
    @ParameterizedTest(name = "interpolate(signal={0}) → concentration≈{1}")
    @CsvSource({
            "2.302585,   10.0",
            "4.605170,  100.0",
            "6.907755, 1000.0"
    })
    @DisplayName("interpolate() recovers concentration within 1% of true value")
    void interpolate_shouldRecoverConcentration(double signal, double expectedConc) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double interpolated = fitter.interpolate(signal, params);
        assertThat(interpolated).isCloseTo(expectedConc, withPercentage(INTERP_TOLERANCE_PCT));
    }

    // -------------------------------------------------------------------------
    // "Asymptote" boundary equivalents
    // -------------------------------------------------------------------------

    /**
     * The semi-log model has no finite asymptotes. The equivalent upper-boundary
     * test is a signal so large that {@link Math#exp} overflows to {@code Infinity}.
     *
     * <p>Constructed params: m = 1e-300 (nearly zero slope). Then
     * {@code (signal − q) / m = 2.0 / 1e-300 ≈ 2e300}, and
     * {@code Math.exp(2e300) = Infinity}. The NaN/Infinity guard must fire.</p>
     */
    @Test
    @DisplayName("interpolate() throws when exp() overflows to Infinity (tiny but non-degenerate slope)")
    void interpolate_shouldThrowForSignalCausingInfinity() {
        // m = 1e-11 is above the 1e-12 degenerate threshold so the slope guard does NOT fire.
        // (2.0 - 0) / 1e-11 = 2e11; Math.exp(2e11) = Infinity → NaN/Infinity guard fires.
        CurveParameters nearDegenerateParams = new CurveParameters(Map.of(
                SemiLogLinearFitter.PARAM_M, 1e-11,
                SemiLogLinearFitter.PARAM_Q, 0.0
        ));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(2.0, nearDegenerateParams))
                .withMessageContaining("NaN/Infinity");
    }

    /**
     * The equivalent lower-boundary test is a negative infinity exponent, which
     * produces {@code exp(-Infinity) = 0.0} — a finite result, so no guard fires.
     * This test documents that the model handles very negative signals gracefully
     * (returns a concentration approaching zero, not an error).
     */
    @Test
    @DisplayName("interpolate() returns near-zero concentration for very negative signal")
    void interpolate_shouldReturnNearZeroForVeryNegativeSignal() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        // Very negative signal → exp(very negative) → 0 (not Infinity)
        double result = fitter.interpolate(-100.0, params);
        assertThat(result).isGreaterThanOrEqualTo(0.0).isFinite();
    }

    // -------------------------------------------------------------------------
    // Minimum-points and zero-concentration validation
    // -------------------------------------------------------------------------

    /**
     * Attempting to fit with fewer than 2 points must throw immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException when fewer than 2 points are supplied")
    void fit_shouldThrowWhenTooFewPoints() {
        List<CalibrationPoint> tooFew = List.of(new CalibrationPoint(10.0, 2.302585));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(tooFew))
                .withMessageContaining("at least 2");
    }

    /**
     * Attempting to fit with a {@code null} list must throw immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException for null point list")
    void fit_shouldThrowForNullPoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(null))
                .withMessageContaining("at least 2");
    }

    /**
     * A calibration point with concentration ≤ 0 must cause an immediate
     * {@link IllegalArgumentException} because {@code ln(0)} is undefined.
     * Note: unlike nonlinear fitters, semi-log fitting <em>rejects</em> rather
     * than silently filters zero-concentration points, because any such point
     * would invalidate the log-transform.
     */
    @Test
    @DisplayName("fit() throws for zero-concentration calibration point")
    void fit_shouldThrowForZeroConcentrationPoint() {
        List<CalibrationPoint> withZero = List.of(
                new CalibrationPoint(0.0,   0.0),
                new CalibrationPoint(10.0,  2.302585),
                new CalibrationPoint(100.0, 4.605170)
        );
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(withZero))
                .withMessageContaining("concentrations > 0");
    }

    /**
     * Attempting back-calculation when slope m ≈ 0 must throw because division
     * by zero makes the inverse undefined.
     */
    @Test
    @DisplayName("interpolate() throws when slope m ≈ 0 (degenerate model)")
    void interpolate_shouldThrowForNearZeroSlope() {
        CurveParameters degenerateParams = new CurveParameters(Map.of(
                SemiLogLinearFitter.PARAM_M, 0.0,
                SemiLogLinearFitter.PARAM_Q, 0.0
        ));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(1.0, degenerateParams))
                .withMessageContaining("slope m ≈ 0");
    }
}

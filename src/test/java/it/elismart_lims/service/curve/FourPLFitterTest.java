package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

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
}

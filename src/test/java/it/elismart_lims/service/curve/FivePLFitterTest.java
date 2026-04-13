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
}

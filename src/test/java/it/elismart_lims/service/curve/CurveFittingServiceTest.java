package it.elismart_lims.service.curve;

import it.elismart_lims.model.CurveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * Unit tests for {@link CurveFittingService}.
 *
 * <p>Verifies delegation to the correct {@link CurveFitter} implementation and that
 * unimplemented curve types surface as {@link UnsupportedOperationException}.</p>
 */
class CurveFittingServiceTest {

    /**
     * Six noiseless 4PL calibration points.
     * True parameters: A=0, B=1.5, C=50, D=2.0.
     * At the inflection point (x=50): y=1.0 exactly.
     */
    private static final List<CalibrationPoint> FOUR_PL_POINTS = List.of(
            new CalibrationPoint(5.0,   0.06130686),
            new CalibrationPoint(10.0,  0.16419903),
            new CalibrationPoint(25.0,  0.52240775),
            new CalibrationPoint(50.0,  1.00000000),
            new CalibrationPoint(100.0, 1.47759225),
            new CalibrationPoint(200.0, 1.77777778)
    );

    /**
     * Five simple linear calibration points on the line {@code y = 2x + 1}.
     */
    private static final List<CalibrationPoint> LINEAR_POINTS = List.of(
            new CalibrationPoint(1.0, 3.0),
            new CalibrationPoint(2.0, 5.0),
            new CalibrationPoint(3.0, 7.0),
            new CalibrationPoint(4.0, 9.0),
            new CalibrationPoint(5.0, 11.0)
    );

    private CurveFittingService service;

    /** Creates a fresh {@link CurveFittingService} before each test. */
    @BeforeEach
    void setUp() {
        service = new CurveFittingService();
    }

    // -------------------------------------------------------------------------
    // Delegation — FOUR_PARAMETER_LOGISTIC
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link CurveFittingService#fitCurve} with FOUR_PARAMETER_LOGISTIC
     * delegates to {@link FourPLFitter} and returns parameters keyed "A", "B", "C", "D".
     */
    @Test
    @DisplayName("fitCurve(FOUR_PARAMETER_LOGISTIC) returns params with A, B, C, D keys")
    void fitCurve_4PL_shouldReturnExpectedParameterKeys() {
        CurveParameters params = service.fitCurve(CurveType.FOUR_PARAMETER_LOGISTIC, FOUR_PL_POINTS);

        assertThat(params.values()).containsKeys(
                FourPLFitter.PARAM_A,
                FourPLFitter.PARAM_B,
                FourPLFitter.PARAM_C,
                FourPLFitter.PARAM_D
        );
    }

    /**
     * Verifies end-to-end delegation: fit 4PL then interpolate back the inflection point
     * signal (y=1.0 → x≈50) within 5%.
     */
    @Test
    @DisplayName("interpolateConcentration(FOUR_PARAMETER_LOGISTIC) recovers inflection point within 5%")
    void interpolateConcentration_4PL_shouldRecoverInflectionPoint() {
        CurveParameters params = service.fitCurve(CurveType.FOUR_PARAMETER_LOGISTIC, FOUR_PL_POINTS);
        double concentration = service.interpolateConcentration(CurveType.FOUR_PARAMETER_LOGISTIC, 1.0, params);

        assertThat(concentration).isCloseTo(50.0, withPercentage(5.0));
    }

    // -------------------------------------------------------------------------
    // Delegation — LINEAR
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link CurveFittingService#fitCurve} with LINEAR recovers slope and
     * intercept for the exact line {@code y = 2x + 1}.
     */
    @Test
    @DisplayName("fitCurve(LINEAR) recovers slope m=2 and intercept q=1 exactly")
    void fitCurve_linear_shouldRecoverSlopeAndIntercept() {
        CurveParameters params = service.fitCurve(CurveType.LINEAR, LINEAR_POINTS);

        assertThat(params.values().get(LinearFitter.PARAM_M)).isCloseTo(2.0, withPercentage(1.0));
        assertThat(params.values().get(LinearFitter.PARAM_Q)).isCloseTo(1.0, withPercentage(1.0));
    }

    /**
     * Verifies that {@link CurveFittingService#interpolateConcentration} with LINEAR
     * back-calculates x=3.0 from y=7.0 (on the line y=2x+1).
     */
    @Test
    @DisplayName("interpolateConcentration(LINEAR, signal=7) → concentration≈3")
    void interpolateConcentration_linear_shouldBackCalculate() {
        CurveParameters params = service.fitCurve(CurveType.LINEAR, LINEAR_POINTS);
        double concentration = service.interpolateConcentration(CurveType.LINEAR, 7.0, params);

        assertThat(concentration).isCloseTo(3.0, withPercentage(1.0));
    }

    // -------------------------------------------------------------------------
    // Delegation — FIVE_PARAMETER_LOGISTIC
    // -------------------------------------------------------------------------

    /**
     * Six noiseless 5PL calibration points.
     * True parameters: A=0, B=2, C=100, D=3, E=0.5.
     * Computed as {@code y = 3 - 3 / sqrt(1 + (x/100)^2)}.
     */
    private static final List<CalibrationPoint> FIVE_PL_POINTS = List.of(
            new CalibrationPoint(10.0,   0.01487123),
            new CalibrationPoint(50.0,   0.31671843),
            new CalibrationPoint(100.0,  0.87867966),
            new CalibrationPoint(200.0,  1.65835921),
            new CalibrationPoint(500.0,  2.41168447),
            new CalibrationPoint(1000.0, 2.70148766)
    );

    /**
     * Verifies that {@link CurveFittingService#fitCurve} with FIVE_PARAMETER_LOGISTIC
     * delegates to {@link FivePLFitter} and returns parameters keyed "A", "B", "C", "D", "E".
     */
    @Test
    @DisplayName("fitCurve(FIVE_PARAMETER_LOGISTIC) returns params with A, B, C, D, E keys")
    void fitCurve_5PL_shouldReturnExpectedParameterKeys() {
        CurveParameters params = service.fitCurve(CurveType.FIVE_PARAMETER_LOGISTIC, FIVE_PL_POINTS);

        assertThat(params.values()).containsKeys(
                FivePLFitter.PARAM_A,
                FivePLFitter.PARAM_B,
                FivePLFitter.PARAM_C,
                FivePLFitter.PARAM_D,
                FivePLFitter.PARAM_E
        );
    }

    /**
     * Verifies end-to-end delegation: fit 5PL then back-interpolate a known signal
     * (x=100 → y≈0.87868) within 5%.
     */
    @Test
    @DisplayName("interpolateConcentration(FIVE_PARAMETER_LOGISTIC) recovers x=100 within 5%")
    void interpolateConcentration_5PL_shouldRecoverKnownConcentration() {
        CurveParameters params = service.fitCurve(CurveType.FIVE_PARAMETER_LOGISTIC, FIVE_PL_POINTS);
        double concentration = service.interpolateConcentration(
                CurveType.FIVE_PARAMETER_LOGISTIC, 0.87867966, params);

        assertThat(concentration).isCloseTo(100.0, withPercentage(5.0));
    }

    // -------------------------------------------------------------------------
    // All fully implemented types — smoke test
    // -------------------------------------------------------------------------

    /**
     * Smoke test: verifies that {@code fitCurve} completes without exception for every
     * {@link CurveType} and returns a non-null {@link CurveParameters}.
     * Uses the 4PL dataset for all types; 5PL converges because 4PL is a special case (E=1).
     */
    @ParameterizedTest(name = "fitCurve({0}) should not throw")
    @EnumSource(CurveType.class)
    @DisplayName("fitCurve() completes for all CurveTypes")
    void fitCurve_allTypes_shouldNotThrow(CurveType type) {
        assertThatNoException().isThrownBy(() -> {
            CurveParameters params = service.fitCurve(type, FOUR_PL_POINTS);
            assertThat(params).isNotNull();
            assertThat(params.values()).isNotEmpty();
        });
    }
}

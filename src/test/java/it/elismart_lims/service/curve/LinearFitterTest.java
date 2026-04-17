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
 * Tests for {@link LinearFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>Generated from {@code y = 2·x + 1} (m=2, q=1):</p>
 * <pre>
 *   x = 1  → y = 3
 *   x = 2  → y = 5
 *   x = 3  → y = 7
 *   x = 4  → y = 9
 *   x = 5  → y = 11
 * </pre>
 */
class LinearFitterTest {

    private static final double TRUE_M = 2.0;
    private static final double TRUE_Q = 1.0;

    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(1.0,  3.0),
            new CalibrationPoint(2.0,  5.0),
            new CalibrationPoint(3.0,  7.0),
            new CalibrationPoint(4.0,  9.0),
            new CalibrationPoint(5.0, 11.0)
    );

    private LinearFitter fitter;

    @BeforeEach
    void setUp() {
        fitter = new LinearFitter();
    }

    @Test
    @DisplayName("fit() recovers slope and intercept within 1% of true values")
    void fit_shouldRecoverParameters() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values().get(LinearFitter.PARAM_M))
                .isCloseTo(TRUE_M, withPercentage(1.0));
        assertThat(params.values().get(LinearFitter.PARAM_Q))
                .isCloseTo(TRUE_Q, offset(0.01));
    }

    @Test
    @DisplayName("fit() populates _r2, _rmse, _df for noiseless data")
    void fit_shouldPopulateGoodnessOfFitMetrics() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);

        assertThat(params.values()).containsKey(CurveParameters.META_R2);
        assertThat(params.values()).containsKey(CurveParameters.META_RMSE);
        assertThat(params.values()).containsKey(CurveParameters.META_DF);

        // Noiseless exact data → perfect fit
        assertThat(params.values().get(CurveParameters.META_R2)).isCloseTo(1.0, offset(1e-6));
        assertThat(params.values().get(CurveParameters.META_RMSE)).isCloseTo(0.0, offset(1e-6));
        // df = 5 points - 2 parameters = 3
        assertThat(params.values().get(CurveParameters.META_DF)).isCloseTo(3.0, offset(1e-10));

        // Linear fitters do NOT emit _rms or _convergence
        assertThat(params.values()).doesNotContainKey(CurveParameters.META_RMS);
        assertThat(params.values()).doesNotContainKey(CurveParameters.META_CONVERGENCE);
    }

    @ParameterizedTest(name = "interpolate(signal={0}) → concentration≈{1}")
    @CsvSource({
            " 3.0, 1.0",
            " 7.0, 3.0",
            "11.0, 5.0"
    })
    @DisplayName("interpolate() recovers concentration from signal")
    void interpolate_shouldRecoverConcentration(double signal, double expectedConc) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThat(fitter.interpolate(signal, params))
                .isCloseTo(expectedConc, withPercentage(1.0));
    }

    @Test
    @DisplayName("fit() throws for fewer than 2 points")
    void fit_shouldThrowForTooFewPoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(List.of(new CalibrationPoint(1.0, 2.0))))
                .withMessageContaining("at least 2");
    }

    @Test
    @DisplayName("fit() throws for null list")
    void fit_shouldThrowForNullPoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.fit(null))
                .withMessageContaining("at least 2");
    }

    @Test
    @DisplayName("interpolate() throws when slope ≈ 0")
    void interpolate_shouldThrowForNearZeroSlope() {
        CurveParameters degenerate = new CurveParameters(
                java.util.Map.of(LinearFitter.PARAM_M, 0.0, LinearFitter.PARAM_Q, 1.0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(5.0, degenerate))
                .withMessageContaining("slope m ≈ 0");
    }
}

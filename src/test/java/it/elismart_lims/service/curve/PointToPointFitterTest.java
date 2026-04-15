package it.elismart_lims.service.curve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;

/**
 * Tests for {@link PointToPointFitter}.
 *
 * <h2>Reference dataset</h2>
 * <p>A simple monotonically increasing calibration table:</p>
 * <pre>
 *   x =  10 → y = 100
 *   x =  20 → y = 200
 *   x =  40 → y = 350
 *   x =  80 → y = 550
 *   x = 160 → y = 800
 * </pre>
 *
 * <p>The point-to-point model stores the table verbatim and interpolates linearly
 * between adjacent points. It has no fitted parameters and no asymptotes in the
 * parametric sense; the "upper" and "lower" boundary tests are the signals at
 * the extreme table edges.</p>
 */
class PointToPointFitterTest {

    /** Absolute tolerance for linearly interpolated concentrations. */
    private static final double ABS_TOLERANCE = 0.01;

    /**
     * Reference calibration table.
     */
    private static final List<CalibrationPoint> REFERENCE_POINTS = List.of(
            new CalibrationPoint(10.0,  100.0),
            new CalibrationPoint(20.0,  200.0),
            new CalibrationPoint(40.0,  350.0),
            new CalibrationPoint(80.0,  550.0),
            new CalibrationPoint(160.0, 800.0)
    );

    private PointToPointFitter fitter;

    /** Creates a fresh {@link PointToPointFitter} before each test. */
    @BeforeEach
    void setUp() {
        fitter = new PointToPointFitter();
    }

    // -------------------------------------------------------------------------
    // Back-interpolation — valid range
    // -------------------------------------------------------------------------

    /**
     * Verifies that back-interpolating a signal that falls exactly on a calibration
     * point returns the corresponding concentration.
     *
     * @param signal       measured signal (at a calibration point)
     * @param expectedConc expected concentration
     */
    @ParameterizedTest(name = "interpolate(signal={0}) → concentration={1}")
    @CsvSource({
            "100.0,  10.0",
            "200.0,  20.0",
            "350.0,  40.0",
            "550.0,  80.0",
            "800.0, 160.0"
    })
    @DisplayName("interpolate() returns exact concentration for signal at calibration point")
    void interpolate_shouldReturnExactConcentrationAtCalibrationPoint(double signal, double expectedConc) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        double interpolated = fitter.interpolate(signal, params);
        assertThat(interpolated).isCloseTo(expectedConc, offset(ABS_TOLERANCE));
    }

    /**
     * Verifies that linear interpolation between adjacent calibration points gives
     * the correct midpoint concentration.
     *
     * <p>Between (x=10, y=100) and (x=20, y=200): signal=150 → concentration=15.</p>
     */
    @Test
    @DisplayName("interpolate() linearly interpolates between calibration points")
    void interpolate_shouldLinearlyInterpolateBetweenPoints() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        // Midpoint of segment [100, 200] → concentration midpoint [10, 20] = 15
        assertThat(fitter.interpolate(150.0, params)).isCloseTo(15.0, offset(ABS_TOLERANCE));
        // Midpoint of segment [350, 550] → concentration midpoint [40, 80] = 60
        assertThat(fitter.interpolate(450.0, params)).isCloseTo(60.0, offset(ABS_TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // "Asymptote" boundary equivalents — extreme table edges
    // -------------------------------------------------------------------------

    /**
     * Equivalent of the "upper asymptote" test: a signal above the highest
     * calibration value (800) must throw because it is outside the table range.
     */
    @Test
    @DisplayName("interpolate() throws for signal above the highest calibration point (upper boundary)")
    void interpolate_shouldThrowForSignalAboveUpperBoundary() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(801.0, params))
                .withMessageContaining("outside the calibration range");
    }

    /**
     * Equivalent of the "lower asymptote" test: a signal below the lowest
     * calibration value (100) must throw because it is outside the table range.
     */
    @Test
    @DisplayName("interpolate() throws for signal below the lowest calibration point (lower boundary)")
    void interpolate_shouldThrowForSignalBelowLowerBoundary() {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(99.0, params))
                .withMessageContaining("outside the calibration range");
    }

    /**
     * Signals clearly outside the range must throw regardless of magnitude.
     *
     * @param signal signal outside [100, 800]
     */
    @ParameterizedTest(name = "interpolate(signal={0}) throws for out-of-range signal")
    @CsvSource({
            "-100.0",
            "0.0",
            "900.0",
            "1000.0"
    })
    @DisplayName("interpolate() throws for signals outside the calibration table range")
    void interpolate_shouldThrowForOutOfRangeSignal(double signal) {
        CurveParameters params = fitter.fit(REFERENCE_POINTS);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> fitter.interpolate(signal, params))
                .withMessageContaining("outside the calibration range");
    }

    // -------------------------------------------------------------------------
    // Flat-segment handling
    // -------------------------------------------------------------------------

    /**
     * A flat calibration segment (dy ≈ 0) must return the midpoint concentration
     * rather than throwing a divide-by-zero exception.
     */
    @Test
    @DisplayName("interpolate() returns midpoint concentration for flat segment (dy ≈ 0)")
    void interpolate_shouldReturnMidpointForFlatSegment() {
        List<CalibrationPoint> withFlat = List.of(
                new CalibrationPoint(10.0, 100.0),
                new CalibrationPoint(20.0, 100.0),   // flat: same signal as previous
                new CalibrationPoint(40.0, 300.0)
        );
        CurveParameters params = fitter.fit(withFlat);
        // Signal 100 falls on the flat segment [10,100]→[20,100]; midpoint = 15
        assertThat(fitter.interpolate(100.0, params)).isCloseTo(15.0, offset(ABS_TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Points-not-provided-in-order test (fitter sorts by concentration)
    // -------------------------------------------------------------------------

    /**
     * The fitter must sort calibration points by concentration ascending before
     * storing them, so that interpolation works correctly regardless of input order.
     */
    @Test
    @DisplayName("fit() sorts calibration points by concentration before storing")
    void fit_shouldSortPointsByConcentration() {
        // Supply points in reverse concentration order
        List<CalibrationPoint> reversed = List.of(
                new CalibrationPoint(160.0, 800.0),
                new CalibrationPoint(80.0,  550.0),
                new CalibrationPoint(40.0,  350.0),
                new CalibrationPoint(20.0,  200.0),
                new CalibrationPoint(10.0,  100.0)
        );
        CurveParameters params = fitter.fit(reversed);
        // Interpolation should produce the same result as with sorted input
        assertThat(fitter.interpolate(150.0, params)).isCloseTo(15.0, offset(ABS_TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Minimum-points validation
    // -------------------------------------------------------------------------

    /**
     * Attempting to fit with fewer than 2 points must throw immediately.
     */
    @Test
    @DisplayName("fit() throws IllegalArgumentException when fewer than 2 points are supplied")
    void fit_shouldThrowWhenTooFewPoints() {
        List<CalibrationPoint> tooFew = List.of(new CalibrationPoint(10.0, 100.0));
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
}

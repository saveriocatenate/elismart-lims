package it.elismart_lims.service.curve;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CurveFitter#estimateHillSlope}.
 *
 * <h2>Reference datasets</h2>
 * <ul>
 *   <li>Steep increasing — 4PL A=0, B=3, C=50, D=2</li>
 *   <li>Shallow increasing — 4PL A=0, B=0.5, C=50, D=2</li>
 *   <li>Decreasing (competitive) — 4PL A=2, B=1.5, C=50, D=0;
 *       verifies the {@code Math.abs()} fix produces a positive estimate</li>
 * </ul>
 */
class CurveFitterHillSlopeTest {

    // --- Steep increasing: y = 2 - 2 / (1 + (x/50)^3) ---
    // B_true = 3; expected B₀ > 1.0
    private static final double[] X_STEEP = {10.0, 25.0, 50.0, 100.0, 200.0};
    private static final double[] Y_STEEP = {0.01587302, 0.22222222, 1.0, 1.77777778, 1.96923077};

    // --- Shallow increasing: y = 2 - 2 / (1 + sqrt(x/50)) ---
    // B_true = 0.5; expected B₀ < 1.0.
    // Dataset must span the 10%–90% signal range (x ≈ 0.5 to 5000) for the
    // Levitzki formula to correctly estimate B₀ < 1; a truncated range overestimates it.
    private static final double[] X_SHALLOW = {0.5, 5.0, 25.0, 50.0, 100.0, 500.0, 2000.0, 5000.0};
    private static final double[] Y_SHALLOW = {0.18182, 0.48051, 0.82843, 1.0, 1.17157, 1.51949, 1.72695, 1.81818};

    // --- Decreasing (competitive): y = 2 / (1 + (x/50)^1.5) ---
    // Without Math.abs(), the formula returns a negative raw estimate.
    // The abs() fix must make it positive.
    private static final double[] X_DECREASING = {5.0, 10.0, 25.0, 50.0, 100.0, 200.0};
    private static final double[] Y_DECREASING = {1.93871, 1.83574, 1.47722, 1.0, 0.52241, 0.22222};

    @Test
    @DisplayName("estimateHillSlope: steep increasing curve (B=3) → B₀ > 1.0")
    void estimateHillSlope_steepIncreasing_returnsGreaterThanOne() {
        double b0 = CurveFitter.estimateHillSlope(X_STEEP, Y_STEEP);
        assertThat(b0).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("estimateHillSlope: shallow increasing curve (B=0.5) → B₀ < 1.0")
    void estimateHillSlope_shallowIncreasing_returnsLessThanOne() {
        double b0 = CurveFitter.estimateHillSlope(X_SHALLOW, Y_SHALLOW);
        assertThat(b0).isLessThan(1.0);
    }

    @Test
    @DisplayName("estimateHillSlope: decreasing competitive assay → B₀ > 0 (Math.abs fix)")
    void estimateHillSlope_decreasingCurve_returnsPositiveB0() {
        double b0 = CurveFitter.estimateHillSlope(X_DECREASING, Y_DECREASING);
        assertThat(b0).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("estimateHillSlope: flat signal (all equal) → fallback 1.0")
    void estimateHillSlope_flatSignal_returnsFallback() {
        double[] x = {10.0, 25.0, 50.0, 100.0};
        double[] y = {1.0, 1.0, 1.0, 1.0};
        assertThat(CurveFitter.estimateHillSlope(x, y)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("estimateHillSlope: single point → fallback 1.0")
    void estimateHillSlope_singlePoint_returnsFallback() {
        double[] x = {50.0};
        double[] y = {1.0};
        assertThat(CurveFitter.estimateHillSlope(x, y)).isEqualTo(1.0);
    }
}

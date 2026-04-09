package it.elismart_lims.service.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Parameterized tests for {@link ValidationConstants} derived metric formulas.
 *
 * <p>Reference formula (ISO 5725 / CLSI EP15-A3, n=2):</p>
 * <pre>
 *   SD  = |signal1 − signal2| / √2
 *   %CV = (SD / mean) × 100
 * </pre>
 */
class ValidationConstantsTest {

    private static final double TOLERANCE = 1e-3;

    /**
     * Verifies %CV calculation across representative signal pairs.
     *
     * <p>Expected values are derived analytically:</p>
     * <ul>
     *   <li>Equal signals → SD=0 → %CV=0</li>
     *   <li>Both zero → mean=0 (division guard) → %CV=0</li>
     *   <li>s1=100, s2=110: mean=105, SD=10/√2 → %CV=(10/(105·√2))·100 ≈ 6.734</li>
     *   <li>s1=200, s2=100: mean=150, SD=100/√2 → %CV=(100/(150·√2))·100 ≈ 47.140</li>
     * </ul>
     *
     * @param signal1     first replicate signal
     * @param signal2     second replicate signal
     * @param expectedCv  expected %CV value
     */
    @ParameterizedTest(name = "signal1={0}, signal2={1} → cvPct≈{2}")
    @CsvSource({
            "100.0, 100.0, 0.0",
            "0.0,   0.0,   0.0",
            "100.0, 110.0, 6.734",
            "200.0, 100.0, 47.140"
    })
    void calculateCvPercent_shouldMatchIso5725Formula(double signal1, double signal2, double expectedCv) {
        double actual = ValidationConstants.calculateCvPercent(signal1, signal2);
        assertThat(actual).isCloseTo(expectedCv, offset(TOLERANCE));
    }

    /**
     * Verifies that {@code calculateSignalMean} returns the arithmetic mean.
     *
     * @param signal1        first replicate signal
     * @param signal2        second replicate signal
     * @param expectedMean   expected arithmetic mean
     */
    @ParameterizedTest(name = "signal1={0}, signal2={1} → mean={2}")
    @CsvSource({
            "100.0, 100.0, 100.0",
            "0.0,   0.0,   0.0",
            "100.0, 110.0, 105.0",
            "200.0, 100.0, 150.0",
            "0.5,   0.52,  0.51"
    })
    void calculateSignalMean_shouldReturnArithmeticMean(double signal1, double signal2, double expectedMean) {
        double actual = ValidationConstants.calculateSignalMean(signal1, signal2);
        assertThat(actual).isCloseTo(expectedMean, offset(TOLERANCE));
    }
}

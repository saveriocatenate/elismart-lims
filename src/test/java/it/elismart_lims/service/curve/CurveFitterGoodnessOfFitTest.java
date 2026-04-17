package it.elismart_lims.service.curve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;

/**
 * Unit tests for {@link CurveFitter#computeGoodnessOfFit}.
 */
class CurveFitterGoodnessOfFitTest {

    @Test
    @DisplayName("perfect fit: R²=1.0, RMSE=0.0, df=n-nParams")
    void computeGoodnessOfFit_perfectFit() {
        double[] yActual    = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] yPredicted = {1.0, 2.0, 3.0, 4.0, 5.0};

        Map<String, Double> result = CurveFitter.computeGoodnessOfFit(yActual, yPredicted, 2);

        assertThat(result.get(CurveParameters.META_R2)).isCloseTo(1.0, offset(1e-10));
        assertThat(result.get(CurveParameters.META_RMSE)).isCloseTo(0.0, offset(1e-10));
        assertThat(result.get(CurveParameters.META_DF)).isCloseTo(3.0, offset(1e-10)); // 5 - 2
    }

    @Test
    @DisplayName("noisy data: R²=0.995, RMSE=0.1, df=3")
    void computeGoodnessOfFit_noisyData() {
        // yActual mean = 3.0; SS_tot = 10; SS_res = 0.05
        double[] yActual    = {1.0, 2.0, 3.0, 4.0, 5.0};
        double[] yPredicted = {1.1, 1.9, 3.1, 3.9, 5.1};

        Map<String, Double> result = CurveFitter.computeGoodnessOfFit(yActual, yPredicted, 2);

        assertThat(result.get(CurveParameters.META_R2)).isCloseTo(0.995, offset(1e-6));
        assertThat(result.get(CurveParameters.META_RMSE)).isCloseTo(0.1, offset(1e-6));
        assertThat(result.get(CurveParameters.META_DF)).isCloseTo(3.0, offset(1e-10));
    }

    @Test
    @DisplayName("degenerate SS_tot (all yActual equal): R² = NaN")
    void computeGoodnessOfFit_degenerateSsTot() {
        double[] yActual    = {3.0, 3.0, 3.0};
        double[] yPredicted = {1.0, 2.0, 4.0};

        Map<String, Double> result = CurveFitter.computeGoodnessOfFit(yActual, yPredicted, 2);

        assertThat(result.get(CurveParameters.META_R2)).isNaN();
    }

    @Test
    @DisplayName("throws when yActual and yPredicted have different lengths")
    void computeGoodnessOfFit_lengthMismatch_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CurveFitter.computeGoodnessOfFit(
                        new double[]{1.0, 2.0},
                        new double[]{1.0},
                        1))
                .withMessageContaining("same length");
    }

    @Test
    @DisplayName("throws for null inputs")
    void computeGoodnessOfFit_nullInput_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CurveFitter.computeGoodnessOfFit(null, new double[]{1.0}, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CurveFitter.computeGoodnessOfFit(new double[]{1.0}, null, 1));
    }
}

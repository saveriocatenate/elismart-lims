package it.elismart_lims.service.validation;

import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.service.curve.CalibrationPoint;
import it.elismart_lims.service.curve.CurveFittingService;
import it.elismart_lims.service.curve.CurveParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ValidationEngine}.
 *
 * <p>Uses a LINEAR calibration curve ({@code y = 2x + 1}) so expected interpolations
 * are deterministic and easy to verify analytically:
 * {@code interpolated = (signalMean − 1) / 2}.
 * The test signal values are chosen so that {@code signalMean} maps exactly onto the
 * nominal concentration, giving {@code recoveryPct = 100%} for "clean" pairs.</p>
 */
class ValidationEngineTest {

    /**
     * Calibration points on the exact line {@code y = 2x + 1}.
     * Five points are sufficient for LINEAR fitting; the slope and intercept
     * are recovered exactly.
     */
    private static final List<CalibrationPoint> LINEAR_CAL_POINTS = List.of(
            new CalibrationPoint(1.0,  3.0),
            new CalibrationPoint(2.0,  5.0),
            new CalibrationPoint(5.0, 11.0),
            new CalibrationPoint(10.0, 21.0),
            new CalibrationPoint(20.0, 41.0)
    );

    private ValidationEngine engine;
    private CurveParameters linearParams;

    /** Protocol with lenient limits: %CV ≤ 10%, recovery within 100 ± 20%. */
    private Protocol lenientProtocol;

    /** Protocol with strict %CV limit: %CV ≤ 5%. */
    private Protocol strictCvProtocol;

    @BeforeEach
    void setUp() {
        CurveFittingService curveFittingService = new CurveFittingService(new com.fasterxml.jackson.databind.ObjectMapper());
        engine = new ValidationEngine(curveFittingService);
        linearParams = curveFittingService.fitCurve(CurveType.LINEAR, LINEAR_CAL_POINTS);

        lenientProtocol = Protocol.builder()
                .id(1L)
                .name("Lenient Protocol")
                .numCalibrationPairs(5)
                .numControlPairs(1)
                .maxCvAllowed(10.0)
                .maxErrorAllowed(20.0)
                .curveType(CurveType.LINEAR)
                .build();

        strictCvProtocol = Protocol.builder()
                .id(2L)
                .name("Strict CV Protocol")
                .numCalibrationPairs(5)
                .numControlPairs(1)
                .maxCvAllowed(5.0)
                .maxErrorAllowed(20.0)
                .curveType(CurveType.LINEAR)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    /**
     * Creates an {@link Experiment} pre-populated with CALIBRATION pairs derived from
     * {@link #LINEAR_CAL_POINTS} and the supplied additional pairs.
     *
     * @param extraPairs non-calibration pairs to add (CONTROL or SAMPLE)
     * @return a fully wired experiment entity
     */
    private Experiment experimentWithPairs(List<MeasurementPair> extraPairs) {
        Experiment experiment = Experiment.builder()
                .id(99L)
                .name("Test Experiment")
                .date(LocalDateTime.now())
                .status(ExperimentStatus.COMPLETED)
                .protocol(lenientProtocol)
                .build();

        long idSeq = 1L;
        for (CalibrationPoint cp : LINEAR_CAL_POINTS) {
            double mean = cp.signal();
            double cv = 0.0;
            MeasurementPair cal = MeasurementPair.builder()
                    .id(idSeq++)
                    .pairType(PairType.CALIBRATION)
                    .concentrationNominal(cp.concentration())
                    .signal1(mean)
                    .signal2(mean)
                    .signalMean(mean)
                    .cvPct(cv)
                    .isOutlier(false)
                    .build();
            experiment.addMeasurementPair(cal);
        }

        for (MeasurementPair p : extraPairs) {
            p.setId(idSeq++);
            experiment.addMeasurementPair(p);
        }
        return experiment;
    }

    /**
     * Creates a non-outlier CONTROL pair with identical duplicate signals.
     * Signal formula: {@code signal = 2 × nominal + 1} → recovery = 100%.
     *
     * @param nominal nominal concentration for the control
     * @return a CONTROL pair with zero %CV and perfect recovery
     */
    private MeasurementPair perfectControlPair(double nominal) {
        double signal = 2.0 * nominal + 1.0;
        double cv = ValidationConstants.calculateCvPercent(signal, signal);
        return MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(nominal)
                .signal1(signal)
                .signal2(signal)
                .signalMean(signal)
                .cvPct(cv)
                .isOutlier(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: all non-outlier pairs pass → overall OK
    // -------------------------------------------------------------------------

    /**
     * Dataset with one CONTROL and one SAMPLE, both with zero %CV and 100% recovery.
     * Expected: {@link ExperimentStatus#OK}.
     */
    @Test
    @DisplayName("all pairs within limits → overall status OK")
    void evaluate_allPairsPass_shouldReturnOk() {
        MeasurementPair control = perfectControlPair(10.0);
        MeasurementPair sample = perfectControlPair(20.0);
        sample.setPairType(PairType.SAMPLE);

        Experiment experiment = experimentWithPairs(List.of(control, sample));

        ValidationResult result = engine.evaluate(experiment, lenientProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.OK);
        assertThat(result.pairResults()).hasSize(2);
        assertThat(result.pairResults()).allSatisfy(pr -> {
            assertThat(pr.cvPass()).isTrue();
            assertThat(pr.recoveryPass()).isTrue();
            assertThat(pr.calculatedRecovery()).isCloseTo(100.0, within(1.0));
        });
    }

    // -------------------------------------------------------------------------
    // Test 2: one pair with %CV above the limit → overall KO
    // -------------------------------------------------------------------------

    /**
     * A CONTROL pair with high %CV (≈ 20%) against a strict limit of 5%.
     * Expected: {@link ExperimentStatus#KO}, {@code cvPass = false} for that pair.
     */
    @Test
    @DisplayName("one pair with cvPct above limit → overall status KO and cvPass=false")
    void evaluate_oneHighCvPair_shouldReturnKo() {
        // signal1=18, signal2=24 → mean=21, SD=|18-24|/√2≈4.24, CV≈20.2%
        double nominal = 10.0;
        double signal1 = 18.0;
        double signal2 = 24.0;
        double mean = ValidationConstants.calculateSignalMean(signal1, signal2);
        double cv   = ValidationConstants.calculateCvPercent(signal1, signal2);

        MeasurementPair highCvControl = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(nominal)
                .signal1(signal1)
                .signal2(signal2)
                .signalMean(mean)
                .cvPct(cv)
                .isOutlier(false)
                .build();

        Experiment experiment = experimentWithPairs(List.of(highCvControl));

        ValidationResult result = engine.evaluate(experiment, strictCvProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.KO);
        assertThat(result.pairResults()).hasSize(1);
        PairValidationResult pr = result.pairResults().getFirst();
        assertThat(pr.cvPass()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Test 3: outlier-flagged pair is excluded, remaining pairs determine status
    // -------------------------------------------------------------------------

    /**
     * One outlier CONTROL (high CV, flagged) and one clean CONTROL.
     * The engine must skip the outlier so the overall outcome is OK, not KO.
     * Expected: {@link ExperimentStatus#OK}, only one {@link PairValidationResult}.
     */
    @Test
    @DisplayName("outlier pair is excluded; remaining pairs determine overall status")
    void evaluate_outlierExcluded_remainingPairsDetermineStatus() {
        // Outlier pair — would fail CV check if evaluated
        double signal1 = 10.0;
        double signal2 = 40.0;
        MeasurementPair outlierPair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(10.0)
                .signal1(signal1)
                .signal2(signal2)
                .signalMean(ValidationConstants.calculateSignalMean(signal1, signal2))
                .cvPct(ValidationConstants.calculateCvPercent(signal1, signal2))
                .isOutlier(true)
                .build();

        // Clean pair — passes all limits
        MeasurementPair cleanPair = perfectControlPair(10.0);

        Experiment experiment = experimentWithPairs(List.of(outlierPair, cleanPair));

        ValidationResult result = engine.evaluate(experiment, strictCvProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.OK);
        // Only the clean (non-outlier) pair should appear in results
        assertThat(result.pairResults()).hasSize(1);
        PairValidationResult pr = result.pairResults().getFirst();
        assertThat(pr.cvPass()).isTrue();
        assertThat(pr.recoveryPass()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Test 5: CONTROL with concentrationNominal = 0 → recovery skipped, not KO
    // -------------------------------------------------------------------------

    /**
     * A CONTROL pair whose {@code concentrationNominal} is exactly 0 (blank well).
     * The engine must skip %Recovery and must NOT mark the pair KO for that reason.
     * Expected: {@link ExperimentStatus#OK}, {@code recoveryPass = true},
     * {@code calculatedRecovery = null}, {@code outOfCalibrationRange = false}.
     */
    @Test
    @DisplayName("CONTROL with concentrationNominal=0 → recovery skipped, pair not KO")
    void evaluate_zeroNominalConcentration_shouldSkipRecovery() {
        double signal = 21.0; // arbitrary — engine should not use it for recovery
        MeasurementPair blankControl = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(0.0)
                .signal1(signal)
                .signal2(signal)
                .signalMean(signal)
                .cvPct(0.0)
                .isOutlier(false)
                .build();

        Experiment experiment = experimentWithPairs(List.of(blankControl));
        ValidationResult result = engine.evaluate(experiment, lenientProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.OK);
        assertThat(result.pairResults()).hasSize(1);
        PairValidationResult pr = result.pairResults().getFirst();
        assertThat(pr.recoveryPass()).isTrue();
        assertThat(pr.calculatedRecovery()).isNull();
        assertThat(pr.outOfCalibrationRange()).isFalse();
        // Pair entity must have recoveryPct=null, not 0 or Infinity
        assertThat(blankControl.getRecoveryPct()).isNull();
    }

    // -------------------------------------------------------------------------
    // Test 6: CONTROL with concentrationNominal = -5 → recovery skipped, not KO
    // -------------------------------------------------------------------------

    /**
     * A CONTROL pair with a negative {@code concentrationNominal} (e.g. an artefact from import).
     * Same expectation as the zero case: recovery is skipped without penalising the pair.
     */
    @Test
    @DisplayName("CONTROL with concentrationNominal=-5 → recovery skipped, pair not KO")
    void evaluate_negativeNominalConcentration_shouldSkipRecovery() {
        double signal = 21.0;
        MeasurementPair negativeNominalControl = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(-5.0)
                .signal1(signal)
                .signal2(signal)
                .signalMean(signal)
                .cvPct(0.0)
                .isOutlier(false)
                .build();

        Experiment experiment = experimentWithPairs(List.of(negativeNominalControl));
        ValidationResult result = engine.evaluate(experiment, lenientProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.OK);
        assertThat(result.pairResults()).hasSize(1);
        PairValidationResult pr = result.pairResults().getFirst();
        assertThat(pr.recoveryPass()).isTrue();
        assertThat(pr.calculatedRecovery()).isNull();
        assertThat(pr.outOfCalibrationRange()).isFalse();
        assertThat(negativeNominalControl.getRecoveryPct()).isNull();
    }

    // -------------------------------------------------------------------------
    // Test 7: signal outside calibration range → negative interpolation → KO
    // -------------------------------------------------------------------------

    /**
     * A CONTROL pair whose signal is so low that the LINEAR curve ({@code y = 2x + 1})
     * back-interpolates to a negative concentration: for {@code signalMean = 0.5},
     * {@code x = (0.5 - 1) / 2 = -0.25}.
     * Expected: {@link ExperimentStatus#KO}, {@code recoveryPass = false},
     * {@code outOfCalibrationRange = true}, {@code calculatedRecovery = null}.
     */
    @Test
    @DisplayName("signal outside calibration range → negative concentration → pair KO, outOfCalibrationRange=true")
    void evaluate_signalOutsideCalibrationRange_shouldMarkOutOfRange() {
        // signalMean = 0.5 → interpolated = (0.5 - 1) / 2 = -0.25 (negative)
        double signal = 0.5;
        MeasurementPair outOfRangeControl = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(10.0)
                .signal1(signal)
                .signal2(signal)
                .signalMean(signal)
                .cvPct(0.0)
                .isOutlier(false)
                .build();

        Experiment experiment = experimentWithPairs(List.of(outOfRangeControl));
        ValidationResult result = engine.evaluate(experiment, lenientProtocol, linearParams);

        assertThat(result.overallStatus()).isEqualTo(ExperimentStatus.KO);
        assertThat(result.pairResults()).hasSize(1);
        PairValidationResult pr = result.pairResults().getFirst();
        assertThat(pr.recoveryPass()).isFalse();
        assertThat(pr.outOfCalibrationRange()).isTrue();
        assertThat(pr.calculatedRecovery()).isNull();
        assertThat(pr.interpolatedConcentration()).isNotNull().isNegative();
        assertThat(outOfRangeControl.getRecoveryPct()).isNull();
    }

    // -------------------------------------------------------------------------
    // Test 4: no calibration pairs → IllegalArgumentException
    // -------------------------------------------------------------------------

    /**
     * An experiment with no CALIBRATION pairs at all.
     * The engine cannot validate without a fitted curve, so it must throw.
     */
    @Test
    @DisplayName("experiment with no CALIBRATION pairs → IllegalArgumentException")
    void evaluate_noCalibrationPairs_shouldThrow() {
        Experiment experiment = Experiment.builder()
                .id(42L)
                .name("No-Cal Experiment")
                .date(LocalDateTime.now())
                .status(ExperimentStatus.COMPLETED)
                .protocol(lenientProtocol)
                .build();

        // Add only a CONTROL pair — no CALIBRATION pairs
        MeasurementPair onlyControl = perfectControlPair(10.0);
        onlyControl.setId(1L);
        experiment.addMeasurementPair(onlyControl);

        assertThatThrownBy(() -> engine.evaluate(experiment, lenientProtocol, linearParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no CALIBRATION pairs found");
    }
}

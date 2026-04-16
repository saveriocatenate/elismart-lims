package it.elismart_lims.mapper;

import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Unit tests for {@link MeasurementPairMapper}.
 */
class MeasurementPairMapperTest {

    @Test
    void toEntity_shouldMapRequestToEntity() {
        var request = new MeasurementPairRequest(PairType.CALIBRATION, 100.0, 0.45, 0.47, 98.5, false);

        var entity = MeasurementPairMapper.toEntity(request);

        assertThat(entity.getPairType()).isEqualTo(PairType.CALIBRATION);
        assertThat(entity.getSignal1()).isEqualTo(0.45);
        assertThat(entity.getIsOutlier()).isFalse();
    }

    @Test
    void toEntity_withExperiment_shouldLinkExperiment() {
        var request = new MeasurementPairRequest(PairType.CALIBRATION, 100.0, 0.45, 0.47, 98.5, false);
        var experiment = Experiment.builder().id(1L).build();

        var entity = MeasurementPairMapper.toEntity(request, experiment);

        assertThat(entity.getExperiment()).isEqualTo(experiment);
    }

    @Test
    void toEntity_shouldDefaultIsOutlierToFalse_whenNull() {
        var request = new MeasurementPairRequest(PairType.CALIBRATION, 100.0, 0.45, 0.47, 98.5, null);

        var entity = MeasurementPairMapper.toEntity(request);

        assertThat(entity.getIsOutlier()).isFalse();
    }

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var entity = MeasurementPair.builder()
                .id(1L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .signal1(0.30)
                .signal2(0.32)
                .signalMean(0.31)
                .cvPct(4.5)
                .recoveryPct(95.0)
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pairType()).isEqualTo(PairType.CONTROL);
        assertThat(response.signalMean()).isEqualTo(0.31);
    }

    @Test
    void toEntityList_shouldMapMultipleRequests() {
        var requests = List.of(
                new MeasurementPairRequest(PairType.CALIBRATION, 100.0, 0.45, 0.47, 98.5, false),
                new MeasurementPairRequest(PairType.CONTROL, 50.0, 0.30, 0.32, 95.0, false)
        );
        var experiment = Experiment.builder().id(1L).build();

        var entities = MeasurementPairMapper.toEntityList(requests, experiment);

        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).getExperiment()).isEqualTo(experiment);
        assertThat(entities.get(1).getExperiment()).isEqualTo(experiment);
    }

    @Test
    void toEntity_shouldIgnoreClientRecoveryPct() {
        // Client supplies recoveryPct=98.5; server must always set it to null
        // (actual value is computed later from calibration curve, not at creation time)
        var request = new MeasurementPairRequest(PairType.CALIBRATION, 100.0, 0.45, 0.47, 98.5, false);

        var entity = MeasurementPairMapper.toEntity(request);

        assertThat(entity.getRecoveryPct()).isNull();
    }

    @Test
    void toEntity_shouldComputeSignalMeanServerSide() {
        // signal1=100, signal2=110 → mean = (100+110)/2 = 105.0
        var request = new MeasurementPairRequest(PairType.CALIBRATION, null, 100.0, 110.0, null, false);

        var entity = MeasurementPairMapper.toEntity(request);

        assertThat(entity.getSignalMean()).isCloseTo(105.0, offset(1e-9));
    }

    @Test
    void toEntity_shouldComputeCvPctServerSide() {
        // signal1=100, signal2=110 → SD=10/√2, mean=105 → %CV=(10/(105·√2))·100 ≈ 6.734
        var request = new MeasurementPairRequest(PairType.CALIBRATION, null, 100.0, 110.0, null, false);

        var entity = MeasurementPairMapper.toEntity(request);

        assertThat(entity.getCvPct()).isCloseTo(6.734, offset(1e-3));
    }

    @Test
    void toResponse_shouldIncludeSample_whenLinked() {
        var sample = Sample.builder()
                .id(5L)
                .barcode("BC-999")
                .matrixType("Urine")
                .build();
        var entity = MeasurementPair.builder()
                .id(10L)
                .pairType(PairType.SAMPLE)
                .isOutlier(false)
                .sample(sample)
                .build();

        var response = MeasurementPairMapper.toResponse(entity);

        assertThat(response.sample()).isNotNull();
        assertThat(response.sample().id()).isEqualTo(5L);
        assertThat(response.sample().barcode()).isEqualTo("BC-999");
        assertThat(response.sample().matrixType()).isEqualTo("Urine");
    }

    @Test
    void toResponse_shouldHaveNullSample_whenNotLinked() {
        var entity = MeasurementPair.builder()
                .id(11L)
                .pairType(PairType.CALIBRATION)
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(entity);

        assertThat(response.sample()).isNull();
    }

    @Test
    void toResponseList_shouldMapMultipleEntities() {
        var entities = List.of(
                MeasurementPair.builder().id(1L).pairType(PairType.CALIBRATION).isOutlier(false).build(),
                MeasurementPair.builder().id(2L).pairType(PairType.CONTROL).isOutlier(false).build()
        );

        var responses = MeasurementPairMapper.toResponseList(entities);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).pairType()).isEqualTo(PairType.CALIBRATION);
        assertThat(responses.get(1).pairType()).isEqualTo(PairType.CONTROL);
    }

    // ── PairStatus computation tests ──────────────────────────────────────────

    /** Builds a Protocol with the given CV and error (recovery) limits. */
    private static Protocol protocol(double maxCv, double maxErr) {
        return Protocol.builder()
                .maxCvAllowed(maxCv)
                .maxErrorAllowed(maxErr)
                .build();
    }

    @Test
    void pairStatus_nonOutlier_cvOk_recoveryOk_shouldBePass() {
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .cvPct(5.0)
                .recoveryPct(95.0)   // |95 - 100| = 5 ≤ 15 → ok
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.PASS);
    }

    @Test
    void pairStatus_outlier_shouldBeOutlier_regardlessOfLimits() {
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .cvPct(2.0)
                .recoveryPct(98.0)
                .isOutlier(true)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.OUTLIER);
    }

    @Test
    void pairStatus_nonOutlier_cvAboveLimit_shouldBeFail() {
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .cvPct(20.0)         // 20 > 15 → fail
                .recoveryPct(100.0)
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.FAIL);
    }

    @Test
    void pairStatus_nonOutlier_cvOk_recoveryOutsideLimit_shouldBeFail() {
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .cvPct(5.0)
                .recoveryPct(120.0)  // |120 - 100| = 20 > 15 → fail
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.FAIL);
    }

    @Test
    void pairStatus_pendingExperiment_shouldBePending() {
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(50.0)
                .cvPct(2.0)
                .recoveryPct(99.0)
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.PENDING);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.PENDING);
    }

    @Test
    void pairStatus_calibration_cvOk_shouldBePass_withoutRecoveryCheck() {
        // CALIBRATION pairs have no target recovery; recovery check must be skipped entirely.
        var pair = MeasurementPair.builder()
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(100.0)
                .cvPct(3.0)
                .recoveryPct(null)   // no recovery for calibration
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.PASS);
    }

    @Test
    void pairStatus_controlWithZeroConcentration_cvOk_shouldBePass() {
        // concentrationNominal == 0 → recovery was intentionally skipped (guard rule);
        // the pair should not be penalised.
        var pair = MeasurementPair.builder()
                .pairType(PairType.CONTROL)
                .concentrationNominal(0.0)
                .cvPct(4.0)
                .recoveryPct(null)
                .isOutlier(false)
                .build();

        var response = MeasurementPairMapper.toResponse(pair, protocol(15.0, 15.0), ExperimentStatus.OK);

        assertThat(response.pairStatus()).isEqualTo(PairStatus.PASS);
    }
}

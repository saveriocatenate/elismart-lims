package it.elismart_lims.service;

import it.elismart_lims.dto.MeasurementPairUpdateRequest;
import it.elismart_lims.dto.OutlierUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.repository.MeasurementPairRepository;
import it.elismart_lims.service.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MeasurementPairService}.
 */
@ExtendWith(MockitoExtension.class)
class MeasurementPairServiceTest {

    @Mock
    private MeasurementPairRepository measurementPairRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private MeasurementPairService measurementPairService;

    private MeasurementPair pair;

    @BeforeEach
    void setUp() {
        pair = MeasurementPair.builder()
                .pairType(PairType.CALIBRATION)
                .signal1(0.45)
                .signal2(0.47)
                .cvPct(3.04)
                .recoveryPct(98.5)
                .isOutlier(false)
                .build();
    }

    @Test
    void saveAll_shouldPersistPairs() {
        List<MeasurementPair> pairs = List.of(pair);
        when(measurementPairRepository.saveAll(anyList())).thenReturn(pairs);

        var result = measurementPairService.saveAll(pairs);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPairType()).isEqualTo(PairType.CALIBRATION);
        verify(measurementPairRepository, times(1)).saveAll(pairs);
    }

    @Test
    void update_shouldRecalculateMetricsAndSave() {
        var experiment = Experiment.builder().id(1L).build();
        var pairToUpdate = MeasurementPair.builder()
                .id(10L)
                .experiment(experiment)
                .pairType(PairType.CALIBRATION)
                .signal1(0.40)
                .signal2(0.42)
                .signalMean(0.41)
                .cvPct(3.36)
                .concentrationNominal(100.0)
                .recoveryPct(41.0)
                .isOutlier(false)
                .build();

        when(measurementPairRepository.findById(10L)).thenReturn(Optional.of(pairToUpdate));
        when(measurementPairRepository.save(any())).thenReturn(pairToUpdate);

        var request = new MeasurementPairUpdateRequest(10L, 0.50, 0.52, 100.0);
        measurementPairService.update(request, 1L);

        var eps = org.assertj.core.data.Offset.offset(1e-9);
        assertThat(pairToUpdate.getSignal1()).isCloseTo(0.50, eps);
        assertThat(pairToUpdate.getSignal2()).isCloseTo(0.52, eps);
        assertThat(pairToUpdate.getSignalMean()).isCloseTo(0.51, eps);
        // Recovery% is left unchanged since it depends on calibration curve data
        assertThat(pairToUpdate.getRecoveryPct()).isCloseTo(41.0, org.assertj.core.data.Offset.offset(0.01));
        verify(measurementPairRepository).save(pairToUpdate);
    }

    @Test
    void update_shouldThrow_whenPairNotFound() {
        when(measurementPairRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> measurementPairService.update(
                new MeasurementPairUpdateRequest(99L, 0.5, 0.5, null), 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MeasurementPair not found");
    }

    @Test
    void updateOutlier_shouldAuditWithReason_whenFlagChanges() {
        var pairWithId = MeasurementPair.builder()
                .id(20L)
                .pairType(PairType.CONTROL)
                .signal1(0.45)
                .signal2(0.47)
                .signalMean(0.46)
                .cvPct(3.04)
                .isOutlier(false)
                .build();

        when(measurementPairRepository.findById(20L)).thenReturn(Optional.of(pairWithId));
        when(measurementPairRepository.save(any())).thenReturn(pairWithId);

        var request = new OutlierUpdateRequest(true, "Instrument drift detected on this well");
        measurementPairService.updateOutlier(20L, request);

        verify(auditLogService).logChange(
                eq("MeasurementPair"), eq(20L), eq("isOutlier"),
                eq("false"), eq("true"),
                eq("Instrument drift detected on this well"));
        assertThat(pairWithId.getIsOutlier()).isTrue();
    }

    @Test
    void updateOutlier_shouldNotAudit_whenFlagUnchanged() {
        var pairWithId = MeasurementPair.builder()
                .id(21L)
                .pairType(PairType.CONTROL)
                .signal1(0.45)
                .signal2(0.47)
                .isOutlier(true)
                .build();

        when(measurementPairRepository.findById(21L)).thenReturn(Optional.of(pairWithId));
        when(measurementPairRepository.save(any())).thenReturn(pairWithId);

        var request = new OutlierUpdateRequest(true, "some reason");
        measurementPairService.updateOutlier(21L, request);

        verify(auditLogService, never()).logChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_shouldThrow_whenPairBelongsToDifferentExperiment() {
        var experiment = Experiment.builder().id(2L).build();
        var pairToUpdate = MeasurementPair.builder()
                .id(10L)
                .experiment(experiment)
                .pairType(PairType.CALIBRATION)
                .isOutlier(false)
                .build();

        when(measurementPairRepository.findById(10L)).thenReturn(Optional.of(pairToUpdate));

        assertThatThrownBy(() -> measurementPairService.update(
                new MeasurementPairUpdateRequest(10L, 0.5, 0.5, null), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to experiment");
    }
}

package it.elismart_lims.service;

import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.dto.ExperimentUpdateRequest;
import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.UsedReagentBatchUpdateRequest;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.ExperimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExperimentService}.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExperimentServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private ProtocolService protocolService;

    @Mock
    private UsedReagentBatchService usedReagentBatchService;

    @Mock
    private MeasurementPairService measurementPairService;

    @Mock
    private ProtocolReagentSpecService protocolReagentSpecService;

    @InjectMocks
    private ExperimentService experimentService;

    private Protocol protocol;
    private UsedReagentBatch batch;
    private Experiment experiment;
    private ExperimentRequest request;

    @BeforeEach
    void setUp() {
        ReagentCatalog reagent = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .build();

        protocol = Protocol.builder()
                .id(10L)
                .name("ELISA Test")
                .numCalibrationPairs(7)
                .numControlPairs(3)
                .maxCvAllowed(15.0)
                .maxErrorAllowed(10.0)
                .build();

        batch = UsedReagentBatch.builder()
                .id(100L)
                .experiment(null)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build();

        experiment = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .usedReagentBatches(List.of(batch))
                .measurementPairs(List.of())
                .build();

        MeasurementPairRequest pairRequest = new MeasurementPairRequest(
                PairType.CALIBRATION, null, 0.45, 0.47, 98.5, false);

        UsedReagentBatchRequest batchRequest = new UsedReagentBatchRequest(
                1L, "LOT-001", LocalDate.of(2027, 12, 31));

        request = new ExperimentRequest(
                "Test Experiment",
                LocalDateTime.of(2026, 4, 5, 10, 0),
                10L,
                ExperimentStatus.COMPLETED,
                List.of(batchRequest),
                List.of(pairRequest));
    }

    @Test
    void getById_shouldReturnResponse_whenExists() {
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));

        ExperimentResponse result = experimentService.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Test Experiment");
        assertThat(result.status()).isEqualTo(ExperimentStatus.COMPLETED);
        verify(experimentRepository).findById(1L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(experimentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 1");
    }

    @Test
    void create_shouldSaveExperimentAndReturnResponse() {
        when(protocolService.getEntityById(10L)).thenReturn(protocol);
        when(protocolReagentSpecService.getMandatoryReagentIds(10L)).thenReturn(Set.of());
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);
        when(usedReagentBatchService.createAllForExperiment(anyList(), any(Experiment.class)))
                .thenReturn(List.of(batch));
        when(measurementPairService.saveAll(anyList())).thenReturn(List.of());

        ExperimentResponse result = experimentService.create(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Test Experiment");
        assertThat(result.protocolName()).isEqualTo("ELISA Test");
        verify(experimentRepository).save(any(Experiment.class));
        verify(usedReagentBatchService).createAllForExperiment(anyList(), any(Experiment.class));
        verify(measurementPairService).saveAll(anyList());
    }

    @Test
    void create_shouldThrow_whenMandatoryReagentsMissing() {
        when(protocolService.getEntityById(10L)).thenReturn(protocol);
        // mandatory IDs {1,2} but batch only covers {1}
        when(protocolReagentSpecService.getMandatoryReagentIds(10L)).thenReturn(Set.of(1L, 2L));

        assertThatThrownBy(() -> experimentService.create(request))
                .isInstanceOf(ProtocolMismatchException.class)
                .hasMessageContaining("must include all mandatory reagents");
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void delete_shouldRemove_whenExists() {
        when(experimentRepository.existsById(1L)).thenReturn(true);

        experimentService.delete(1L);

        verify(experimentRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(experimentRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> experimentService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 1");
        verify(experimentRepository, never()).deleteById(anyLong());
    }

    @Test
    void search_shouldReturnPagedResultsWithFilters() {
        ExperimentSearchRequest searchRequest = new ExperimentSearchRequest(
                "test", null, null, null, ExperimentStatus.COMPLETED, 0, 10);

        when(experimentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(experiment)));

        ExperimentPage result = experimentService.search(searchRequest);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().name()).isEqualTo("Test Experiment");
        assertThat(result.totalElements()).isEqualTo(1);
        verify(experimentRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void update_shouldUpdateFieldsAndReturnResponse() {
        UsedReagentBatchUpdateRequest batchUpdate = new UsedReagentBatchUpdateRequest(
                100L, "LOT-NEW", LocalDate.of(2028, 6, 30));
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                "Updated Name",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                ExperimentStatus.OK,
                List.of(batchUpdate),
                null);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);

        ExperimentResponse result = experimentService.update(1L, updateRequest);

        assertThat(result.id()).isEqualTo(1L);
        verify(experimentRepository).findById(1L);
        verify(usedReagentBatchService).updateBatch(batchUpdate, 1L);
        verify(experimentRepository).save(any(Experiment.class));
    }

    @Test
    void existsByProtocolId_shouldReturnTrue_whenExperimentsExist() {
        when(experimentRepository.existsByProtocolId(10L)).thenReturn(true);

        assertThat(experimentService.existsByProtocolId(10L)).isTrue();
        verify(experimentRepository).existsByProtocolId(10L);
    }

    @Test
    void update_shouldThrow_whenExperimentNotFound() {
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                "Name",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                ExperimentStatus.OK,
                List.of(),
                null);

        when(experimentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(99L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 99");
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void search_shouldReturnEmpty_whenNoMatches() {
        ExperimentSearchRequest searchRequest = new ExperimentSearchRequest(
                "nonexistent", null, null, null, null, 0, 10);

        when(experimentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ExperimentPage result = experimentService.search(searchRequest);

        assertThat(result.content()).isEmpty();
    }
}

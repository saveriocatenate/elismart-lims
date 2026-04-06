package it.elismart_lims.service;

import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.UsedReagentBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UsedReagentBatchService}.
 */
@ExtendWith(MockitoExtension.class)
class UsedReagentBatchServiceTest {

    @Mock
    private UsedReagentBatchRepository usedReagentBatchRepository;

    @Mock
    private ReagentCatalogService reagentCatalogService;

    @InjectMocks
    private UsedReagentBatchService usedReagentBatchService;

    private ReagentCatalog reagent;
    private UsedReagentBatch batch;
    private Experiment experiment;

    @BeforeEach
    void setUp() {
        reagent = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .build();

        batch = UsedReagentBatch.builder()
                .id(10L)
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
                .build();
    }

    @Test
    void getById_shouldReturnBatch_whenExists() {
        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(batch));

        UsedReagentBatch result = usedReagentBatchService.getById(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getLotNumber()).isEqualTo("LOT-001");
        assertThat(result.getReagent().getId()).isEqualTo(1L);
        verify(usedReagentBatchRepository).findById(10L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedReagentBatchService.getById(10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Used reagent batch not found with id: 10");
    }

    @Test
    void createAllForExperiment_shouldCreateAndReturnBatches() {
        UsedReagentBatchRequest req = new UsedReagentBatchRequest(1L, "LOT-001", LocalDate.of(2027, 12, 31));
        when(reagentCatalogService.getEntityById(1L)).thenReturn(reagent);
        when(usedReagentBatchRepository.save(any(UsedReagentBatch.class))).thenReturn(batch);

        List<UsedReagentBatch> result = usedReagentBatchService.createAllForExperiment(List.of(req), experiment);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLotNumber()).isEqualTo("LOT-001");
        verify(reagentCatalogService).getEntityById(1L);
        verify(usedReagentBatchRepository).save(any(UsedReagentBatch.class));
    }

    @Test
    void createAllForExperiment_shouldThrow_whenReagentNotFound() {
        UsedReagentBatchRequest req = new UsedReagentBatchRequest(99L, "LOT-999", null);
        when(reagentCatalogService.getEntityById(99L))
                .thenThrow(new ResourceNotFoundException("Reagent catalog not found with id: 99"));

        var list = List.of(req);
        assertThatThrownBy(() -> usedReagentBatchService.createAllForExperiment(list, experiment))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reagent catalog not found with id: 99");
        verify(usedReagentBatchRepository, never()).save(any());
    }
}

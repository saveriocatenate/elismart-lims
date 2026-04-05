package it.elismart_lims.service;

import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
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

    @InjectMocks
    private UsedReagentBatchService usedReagentBatchService;

    private UsedReagentBatch batch;

    @BeforeEach
    void setUp() {
        ReagentCatalog reagent = ReagentCatalog.builder()
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
    void linkToExperiment_shouldUpdateExperimentAndSave() {
        Experiment experiment = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status("COMPLETED")
                .build();

        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(usedReagentBatchRepository.save(any(UsedReagentBatch.class))).thenReturn(batch);

        List<UsedReagentBatch> result = usedReagentBatchService.linkToExperiment(List.of(10L), experiment);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getExperiment().getId()).isEqualTo(1L);
        verify(usedReagentBatchRepository).save(any(UsedReagentBatch.class));
    }

    @Test
    void linkToExperiment_shouldThrowWhenAnyBatchNotFound() {
        Experiment experiment = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .build();

        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.empty());

        var list = List.of(10L);
        assertThatThrownBy(() -> usedReagentBatchService.linkToExperiment(list, experiment))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Used reagent batch not found with id: 10");
        verify(usedReagentBatchRepository, never()).save(any());
    }

    @Test
    void getReagentIdsByBatchIds_shouldReturnReagentIds() {
        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(batch));

        List<Long> result = usedReagentBatchService.getReagentIdsByBatchIds(List.of(10L));

        assertThat(result).containsExactly(1L);
    }

    @Test
    void getReagentIdsByBatchIds_shouldHandleMultipleBatches() {
        ReagentCatalog reagent2 = ReagentCatalog.builder().id(2L).name("Buffer").build();
        UsedReagentBatch batch2 = UsedReagentBatch.builder()
                .id(20L)
                .experiment(null)
                .reagent(reagent2)
                .lotNumber("LOT-002")
                .build();

        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(usedReagentBatchRepository.findById(20L)).thenReturn(Optional.of(batch2));

        List<Long> result = usedReagentBatchService.getReagentIdsByBatchIds(List.of(10L, 20L));

        assertThat(result).containsExactly(1L, 2L);
    }
}

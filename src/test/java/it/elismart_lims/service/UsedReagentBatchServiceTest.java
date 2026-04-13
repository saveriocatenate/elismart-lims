package it.elismart_lims.service;

import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.UsedReagentBatchUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.ReagentBatch;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UsedReagentBatchService}.
 */
@ExtendWith(MockitoExtension.class)
class UsedReagentBatchServiceTest {

    @Mock
    private UsedReagentBatchRepository usedReagentBatchRepository;

    @Mock
    private ReagentBatchService reagentBatchService;

    @InjectMocks
    private UsedReagentBatchService usedReagentBatchService;

    private ReagentCatalog reagent;
    private ReagentBatch reagentBatch;
    private UsedReagentBatch link;
    private Experiment experiment;

    @BeforeEach
    void setUp() {
        reagent = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .build();

        reagentBatch = ReagentBatch.builder()
                .id(5L)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build();

        link = UsedReagentBatch.builder()
                .id(10L)
                .reagent(reagent)
                .reagentBatch(reagentBatch)
                .build();

        experiment = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .build();
    }

    @Test
    void getById_shouldReturnLink_whenExists() {
        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(link));

        UsedReagentBatch result = usedReagentBatchService.getById(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getReagentBatch().getLotNumber()).isEqualTo("LOT-001");
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
    void createAllForExperiment_shouldCreateAndReturnLinks() {
        UsedReagentBatchRequest req = new UsedReagentBatchRequest(5L);
        when(reagentBatchService.getEntityById(5L)).thenReturn(reagentBatch);
        when(usedReagentBatchRepository.save(any(UsedReagentBatch.class))).thenReturn(link);

        List<UsedReagentBatch> result = usedReagentBatchService.createAllForExperiment(List.of(req), experiment);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getReagentBatch().getLotNumber()).isEqualTo("LOT-001");
        verify(reagentBatchService).getEntityById(5L);
        verify(usedReagentBatchRepository).save(argThat(b ->
                b.getReagentBatch() == reagentBatch && b.getReagent() == reagent));
    }

    @Test
    void createAllForExperiment_shouldThrow_whenBatchNotFound() {
        UsedReagentBatchRequest req = new UsedReagentBatchRequest(99L);
        when(reagentBatchService.getEntityById(99L))
                .thenThrow(new ResourceNotFoundException("ReagentBatch not found with id: 99"));

        var list = List.of(req);
        assertThatThrownBy(() -> usedReagentBatchService.createAllForExperiment(list, experiment))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ReagentBatch not found with id: 99");
        verify(usedReagentBatchRepository, never()).save(any());
    }

    @Test
    void updateBatch_shouldRelinkToNewBatch() {
        ReagentBatch newBatch = ReagentBatch.builder()
                .id(6L)
                .reagent(reagent)
                .lotNumber("LOT-NEW")
                .expiryDate(LocalDate.of(2028, 6, 30))
                .build();

        UsedReagentBatch linkWithExp = UsedReagentBatch.builder()
                .id(10L)
                .experiment(experiment)
                .reagent(reagent)
                .reagentBatch(reagentBatch)
                .build();

        UsedReagentBatchUpdateRequest request = new UsedReagentBatchUpdateRequest(10L, 6L);

        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(linkWithExp));
        when(reagentBatchService.getEntityById(6L)).thenReturn(newBatch);
        when(usedReagentBatchRepository.save(any(UsedReagentBatch.class))).thenReturn(linkWithExp);

        usedReagentBatchService.updateBatch(request, 1L);

        verify(usedReagentBatchRepository).save(argThat(b ->
                b.getReagentBatch() == newBatch && b.getReagent() == reagent));
    }

    @Test
    void updateBatch_shouldThrow_whenLinkNotFound() {
        UsedReagentBatchUpdateRequest request = new UsedReagentBatchUpdateRequest(999L, 5L);
        when(usedReagentBatchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedReagentBatchService.updateBatch(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Used reagent batch not found with id: 999");
        verify(usedReagentBatchRepository, never()).save(any());
    }

    @Test
    void updateBatch_shouldThrow_whenLinkBelongsToDifferentExperiment() {
        Experiment otherExp = Experiment.builder().id(99L).build();
        UsedReagentBatch linkOtherExp = UsedReagentBatch.builder()
                .id(10L)
                .experiment(otherExp)
                .reagent(reagent)
                .reagentBatch(reagentBatch)
                .build();
        UsedReagentBatchUpdateRequest request = new UsedReagentBatchUpdateRequest(10L, 5L);
        when(usedReagentBatchRepository.findById(10L)).thenReturn(Optional.of(linkOtherExp));

        assertThatThrownBy(() -> usedReagentBatchService.updateBatch(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to experiment");
        verify(usedReagentBatchRepository, never()).save(any());
    }
}

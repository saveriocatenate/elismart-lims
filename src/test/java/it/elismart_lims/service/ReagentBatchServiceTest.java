package it.elismart_lims.service;

import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.dto.ReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ReagentBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReagentBatchService}.
 */
@ExtendWith(MockitoExtension.class)
class ReagentBatchServiceTest {

    @Mock
    private ReagentBatchRepository reagentBatchRepository;

    @Mock
    private ReagentCatalogService reagentCatalogService;

    @InjectMocks
    private ReagentBatchService reagentBatchService;

    private ReagentCatalog reagent;
    private ReagentBatch batch;
    private ReagentBatchCreateRequest request;

    @BeforeEach
    void setUp() {
        reagent = ReagentCatalog.builder().id(10L).name("Anti-IgG").manufacturer("Sigma").build();
        batch = ReagentBatch.builder()
                .id(1L)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .supplier("SupplierX")
                .notes("Keep refrigerated")
                .build();
        request = new ReagentBatchCreateRequest(10L, "LOT-001", LocalDate.of(2026, 12, 31), "SupplierX", "Keep refrigerated");
    }

    // --- create ---

    @Test
    void create_shouldSaveAndReturnResponse() {
        when(reagentCatalogService.getEntityById(10L)).thenReturn(reagent);
        when(reagentBatchRepository.findByReagentIdAndLotNumber(10L, "LOT-001")).thenReturn(Optional.empty());
        when(reagentBatchRepository.save(any(ReagentBatch.class))).thenReturn(batch);

        ReagentBatchResponse result = reagentBatchService.create(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.reagentId()).isEqualTo(10L);
        assertThat(result.lotNumber()).isEqualTo("LOT-001");
        verify(reagentBatchRepository).save(any(ReagentBatch.class));
    }

    @Test
    void create_shouldThrow_whenReagentNotFound() {
        when(reagentCatalogService.getEntityById(10L))
                .thenThrow(new ResourceNotFoundException("Reagent catalog not found with id: 10"));

        assertThatThrownBy(() -> reagentBatchService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reagent catalog not found with id: 10");
        verify(reagentBatchRepository, never()).save(any());
    }

    @Test
    void create_shouldThrow_whenDuplicateLot() {
        when(reagentCatalogService.getEntityById(10L)).thenReturn(reagent);
        when(reagentBatchRepository.findByReagentIdAndLotNumber(10L, "LOT-001")).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> reagentBatchService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LOT-001")
                .hasMessageContaining("already exists");
        verify(reagentBatchRepository, never()).save(any());
    }

    // --- findByReagentId ---

    @Test
    void findByReagentId_shouldReturnList() {
        when(reagentBatchRepository.findByReagentId(10L)).thenReturn(List.of(batch));

        List<ReagentBatchResponse> result = reagentBatchService.findByReagentId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().lotNumber()).isEqualTo("LOT-001");
        verify(reagentBatchRepository).findByReagentId(10L);
    }

    @Test
    void findByReagentId_shouldReturnEmptyList_whenNoBatches() {
        when(reagentBatchRepository.findByReagentId(10L)).thenReturn(List.of());

        List<ReagentBatchResponse> result = reagentBatchService.findByReagentId(10L);

        assertThat(result).isEmpty();
    }

    // --- findById ---

    @Test
    void findById_shouldReturnResponse_whenExists() {
        when(reagentBatchRepository.findById(1L)).thenReturn(Optional.of(batch));

        ReagentBatchResponse result = reagentBatchService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.lotNumber()).isEqualTo("LOT-001");
    }

    @Test
    void findById_shouldThrow_whenNotFound() {
        when(reagentBatchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reagentBatchService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ReagentBatch not found with id: 99");
    }
}

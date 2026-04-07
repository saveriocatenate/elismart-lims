package it.elismart_lims.service;

import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ReagentCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReagentCatalogService}.
 */
@ExtendWith(MockitoExtension.class)
class ReagentCatalogServiceTest {

    @Mock
    private ReagentCatalogRepository reagentCatalogRepository;

    @InjectMocks
    private ReagentCatalogService reagentCatalogService;

    private ReagentCatalog reagent;

    @BeforeEach
    void setUp() {
        reagent = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .description("Goat anti-human IgG")
                .build();
    }

    @Test
    void getAll_shouldReturnPagedResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reagentCatalogRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(reagent)));

        Page<ReagentCatalogResponse> result = reagentCatalogService.getAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Anti-IgG");
        verify(reagentCatalogRepository).findAll(pageable);
    }

    @Test
    void getById_shouldReturnResponse_whenExists() {
        when(reagentCatalogRepository.findById(1L)).thenReturn(Optional.of(reagent));

        ReagentCatalogResponse result = reagentCatalogService.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Anti-IgG");
        assertThat(result.manufacturer()).isEqualTo("Sigma");
        verify(reagentCatalogRepository).findById(1L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(reagentCatalogRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reagentCatalogService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reagent catalog not found with id: 1");
    }

    @Test
    void create_shouldSaveAndReturnEntity() {
        when(reagentCatalogRepository.existsByNameIgnoreCaseAndManufacturerIgnoreCase("Anti-IgG", "Sigma"))
                .thenReturn(false);
        when(reagentCatalogRepository.save(any(ReagentCatalog.class))).thenReturn(reagent);

        ReagentCatalog result = reagentCatalogService.create(reagent);

        assertThat(result.getName()).isEqualTo("Anti-IgG");
        verify(reagentCatalogRepository).save(reagent);
    }

    @Test
    void create_shouldThrow_whenDuplicateNameAndManufacturer() {
        when(reagentCatalogRepository.existsByNameIgnoreCaseAndManufacturerIgnoreCase("Anti-IgG", "Sigma"))
                .thenReturn(true);

        assertThatThrownBy(() -> reagentCatalogService.create(reagent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(reagentCatalogRepository, never()).save(any());
    }

    @Test
    void search_shouldReturnMatchingPage_whenFiltersProvided() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reagentCatalogRepository.search("Anti", "Sigma", pageable))
                .thenReturn(new PageImpl<>(List.of(reagent)));

        Page<ReagentCatalogResponse> result = reagentCatalogService.search("Anti", "Sigma", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Anti-IgG");
        verify(reagentCatalogRepository).search("Anti", "Sigma", pageable);
    }

    @Test
    void search_shouldPassNullParams_whenFiltersBlank() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reagentCatalogRepository.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(reagent)));

        Page<ReagentCatalogResponse> result = reagentCatalogService.search("", "  ", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reagentCatalogRepository).search(null, null, pageable);
    }

    @Test
    void delete_shouldRemove_whenExists() {
        when(reagentCatalogRepository.existsById(1L)).thenReturn(true);

        reagentCatalogService.delete(1L);

        verify(reagentCatalogRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(reagentCatalogRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> reagentCatalogService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reagent catalog not found with id: 1");
        verify(reagentCatalogRepository, never()).deleteById(anyLong());
    }
}

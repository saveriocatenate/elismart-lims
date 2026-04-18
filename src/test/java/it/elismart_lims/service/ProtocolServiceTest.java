package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
import it.elismart_lims.service.audit.AuditLogService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProtocolService}.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolServiceTest {

    @Mock
    private ProtocolRepository protocolRepository;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProtocolService protocolService;

    private Protocol protocol;

    @BeforeEach
    void setUp() {
        protocol = Protocol.builder()
                .name("IgG Test")
                .numCalibrationPairs(7)
                .numControlPairs(3)
                .maxCvAllowed(15.0)
                .maxErrorAllowed(10.0)
                .curveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .concentrationUnit("ng/mL")
                .build();
    }

    @Test
    void getAll_shouldReturnAllProtocols() {
        Protocol protocol2 = Protocol.builder()
                .name("IgM Test")
                .numCalibrationPairs(5)
                .numControlPairs(2)
                .maxCvAllowed(10.0)
                .maxErrorAllowed(8.0)
                .curveType(CurveType.FIVE_PARAMETER_LOGISTIC)
                .build();

        when(protocolRepository.findAll()).thenReturn(List.of(protocol, protocol2));

        var result = protocolService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("IgG Test");
        assertThat(result.get(1).name()).isEqualTo("IgM Test");
        verify(protocolRepository).findAll();
    }

    @Test
    void getAll_shouldReturnEmpty_whenNoProtocols() {
        when(protocolRepository.findAll()).thenReturn(List.of());

        var result = protocolService.getAll();

        assertThat(result).isEmpty();
        verify(protocolRepository).findAll();
    }

    @Test
    void getById_shouldReturnProtocol_whenExists() {
        when(protocolRepository.findById(1L)).thenReturn(Optional.of(protocol));

        var result = protocolService.getById(1L);

        assertThat(result.name()).isEqualTo("IgG Test");
        verify(protocolRepository, times(1)).findById(1L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(protocolRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocolService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Protocol not found with id: 1");
    }

    @Test
    void create_shouldSaveProtocol() {
        when(protocolRepository.existsByNameIgnoreCaseAndNumCalibrationPairsAndNumControlPairs(
                "IgG Test", 7, 3)).thenReturn(false);
        when(protocolRepository.save(any(Protocol.class))).thenReturn(protocol);

        var result = protocolService.create(protocol);

        assertThat(result.name()).isEqualTo("IgG Test");
        verify(protocolRepository, times(1)).save(protocol);
    }

    @Test
    void create_shouldThrow_whenDuplicateProtocol() {
        when(protocolRepository.existsByNameIgnoreCaseAndNumCalibrationPairsAndNumControlPairs(
                "IgG Test", 7, 3)).thenReturn(true);

        assertThatThrownBy(() -> protocolService.create(protocol))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(protocolRepository, never()).save(any());
    }

    @Test
    void search_shouldReturnAllProtocols_whenNameIsBlank() {
        Pageable pageable = PageRequest.of(0, 20);
        when(protocolRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(protocol)));

        Page<it.elismart_lims.dto.ProtocolResponse> result = protocolService.search(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("IgG Test");
        verify(protocolRepository).findAll(pageable);
    }

    @Test
    void search_shouldReturnFiltered_whenNameProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(protocolRepository.findByNameContainingIgnoreCase("IgG", pageable))
                .thenReturn(new PageImpl<>(List.of(protocol)));

        Page<it.elismart_lims.dto.ProtocolResponse> result = protocolService.search("IgG", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("IgG Test");
        verify(protocolRepository).findByNameContainingIgnoreCase("IgG", pageable);
    }

    @Test
    void delete_shouldRemove_whenProtocolExists() {
        when(protocolRepository.existsById(1L)).thenReturn(true);
        when(experimentService.existsByProtocolId(1L)).thenReturn(false);

        protocolService.delete(1L);

        verify(protocolRepository, times(1)).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenProtocolNotFound() {
        when(protocolRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> protocolService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Protocol not found with id: 1");
        verify(protocolRepository, never()).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenProtocolHasLinkedExperiments() {
        when(protocolRepository.existsById(1L)).thenReturn(true);
        when(experimentService.existsByProtocolId(1L)).thenReturn(true);

        assertThatThrownBy(() -> protocolService.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remove all experiments");
        verify(protocolRepository, never()).deleteById(1L);
    }

    @Test
    void update_shouldUpdateFieldsAndReturnResponse() {
        protocol.setId(1L);
        ProtocolRequest updateRequest = new ProtocolRequest("IgG v2", 7, 3, 12.0, 8.0, CurveType.FOUR_PARAMETER_LOGISTIC, "IU/mL");
        when(protocolRepository.findById(1L)).thenReturn(Optional.of(protocol));
        when(experimentService.existsByProtocolId(1L)).thenReturn(false);
        when(protocolRepository.save(any(Protocol.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = protocolService.update(1L, updateRequest);

        assertThat(result.name()).isEqualTo("IgG v2");
        assertThat(result.concentrationUnit()).isEqualTo("IU/mL");
        verify(protocolRepository).save(protocol);
    }

    @Test
    void update_shouldThrow_whenProtocolHasLinkedExperiments() {
        protocol.setId(1L);
        ProtocolRequest updateRequest = new ProtocolRequest("IgG v2", 7, 3, 12.0, 8.0, CurveType.FOUR_PARAMETER_LOGISTIC, "ng/mL");
        when(protocolRepository.findById(1L)).thenReturn(Optional.of(protocol));
        when(experimentService.existsByProtocolId(1L)).thenReturn(true);

        assertThatThrownBy(() -> protocolService.update(1L, updateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remove all experiments");
        verify(protocolRepository, never()).save(any());
    }

    @Test
    void getByName_shouldReturnProtocol_whenNameExists() {
        when(protocolRepository.findByName("IgG Test")).thenReturn(Optional.of(protocol));

        var result = protocolService.getByName("IgG Test");

        assertThat(result.name()).isEqualTo("IgG Test");
        assertThat(result.maxCvAllowed()).isEqualTo(15.0);
        verify(protocolRepository, times(1)).findByName("IgG Test");
    }

    @Test
    void getByName_shouldThrow_whenNameNotFound() {
        when(protocolRepository.findByName("Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocolService.getByName("Unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Protocol not found with name: Unknown");
    }
}

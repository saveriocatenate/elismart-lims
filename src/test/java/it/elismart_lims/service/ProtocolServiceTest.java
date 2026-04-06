package it.elismart_lims.service;

import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
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
        when(protocolRepository.save(any(Protocol.class))).thenReturn(protocol);

        var result = protocolService.create(protocol);

        assertThat(result.name()).isEqualTo("IgG Test");
        verify(protocolRepository, times(1)).save(protocol);
    }

    @Test
    void delete_shouldRemove_whenProtocolExists() {
        when(protocolRepository.existsById(1L)).thenReturn(true);

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
}

package it.elismart_lims.service;

import it.elismart_lims.dto.SampleCreateRequest;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.dto.SampleUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Sample;
import it.elismart_lims.repository.SampleRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SampleService}.
 */
@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

    @Mock
    private SampleRepository sampleRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SampleService sampleService;

    private Sample sample;

    @BeforeEach
    void setUp() {
        sample = Sample.builder()
                .id(1L)
                .barcode("BC-001")
                .matrixType("Serum")
                .patientId("P-100")
                .studyId("STU-2025")
                .collectionDate(LocalDate.of(2025, 1, 15))
                .preparationMethod("1:10 dilution")
                .notes("Fast sample")
                .build();
    }

    @Test
    void getAll_shouldReturnPagedResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        when(sampleRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(sample)));

        Page<SampleResponse> result = sampleService.getAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().barcode()).isEqualTo("BC-001");
        verify(sampleRepository).findAll(pageable);
    }

    @Test
    void getById_shouldReturnResponse_whenExists() {
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));

        SampleResponse result = sampleService.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.barcode()).isEqualTo("BC-001");
        assertThat(result.matrixType()).isEqualTo("Serum");
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(sampleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sampleService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sample not found with id: 99");
    }

    @Test
    void getByBarcode_shouldReturnResponse_whenExists() {
        when(sampleRepository.findByBarcode("BC-001")).thenReturn(Optional.of(sample));

        SampleResponse result = sampleService.getByBarcode("BC-001");

        assertThat(result.barcode()).isEqualTo("BC-001");
        assertThat(result.patientId()).isEqualTo("P-100");
    }

    @Test
    void getByBarcode_shouldThrow_whenNotFound() {
        when(sampleRepository.findByBarcode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sampleService.getByBarcode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sample not found with barcode: UNKNOWN");
    }

    @Test
    void create_shouldPersistAndReturnResponse() {
        var request = new SampleCreateRequest("BC-002", "Plasma", "P-200", "STU-2025",
                LocalDate.of(2025, 2, 1), "neat", null);
        Sample built = Sample.builder().id(2L).barcode("BC-002").matrixType("Plasma").build();

        when(sampleRepository.existsByBarcode("BC-002")).thenReturn(false);
        when(sampleRepository.save(any(Sample.class))).thenReturn(built);

        SampleResponse result = sampleService.create(request);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.barcode()).isEqualTo("BC-002");
        verify(sampleRepository).save(any(Sample.class));
    }

    @Test
    void create_shouldThrow_whenBarcodeAlreadyExists() {
        var request = new SampleCreateRequest("BC-001", null, null, null, null, null, null);
        when(sampleRepository.existsByBarcode("BC-001")).thenReturn(true);

        assertThatThrownBy(() -> sampleService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BC-001");
        verify(sampleRepository, never()).save(any());
    }

    @Test
    void update_shouldApplyChangesAndAudit() {
        var request = new SampleUpdateRequest("Plasma", null, null, null, null, "Updated note");
        when(sampleRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(sampleRepository.save(any(Sample.class))).thenReturn(sample);

        SampleResponse result = sampleService.update(1L, request);

        assertThat(result).isNotNull();
        // matrixType and notes changed → 2 audit entries expected
        verify(auditLogService, times(2)).logChange(
                eq("Sample"), eq(1L), any(), any(), any(), isNull());
        verify(sampleRepository).save(sample);
    }

    @Test
    void update_shouldThrow_whenNotFound() {
        when(sampleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sampleService.update(99L,
                new SampleUpdateRequest(null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sample not found with id: 99");
    }

    @Test
    void delete_shouldRemove_whenExists() {
        when(sampleRepository.existsById(1L)).thenReturn(true);

        sampleService.delete(1L);

        verify(sampleRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(sampleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> sampleService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sample not found with id: 99");
        verify(sampleRepository, never()).deleteById(anyLong());
    }
}

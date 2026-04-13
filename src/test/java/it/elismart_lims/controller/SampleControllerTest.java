package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.dto.SampleCreateRequest;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.dto.SampleUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.SampleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link SampleController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(SampleController.class)
class SampleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SampleService sampleService;

    private SampleResponse sampleResponse() {
        return SampleResponse.builder()
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
    void getAll_shouldReturnPagedSamples() throws Exception {
        when(sampleService.getAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse())));

        mockMvc.perform(get("/api/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].barcode").value("BC-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getById_shouldReturnSample() throws Exception {
        when(sampleService.getById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/samples/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.barcode").value("BC-001"));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(sampleService.getById(99L)).thenThrow(new ResourceNotFoundException("Sample not found with id: 99"));

        mockMvc.perform(get("/api/samples/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByBarcode_shouldReturnSample() throws Exception {
        when(sampleService.getByBarcode("BC-001")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/samples/barcode/BC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("BC-001"))
                .andExpect(jsonPath("$.matrixType").value("Serum"));
    }

    @Test
    void getByBarcode_shouldReturn404_whenNotFound() throws Exception {
        when(sampleService.getByBarcode("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Sample not found with barcode: UNKNOWN"));

        mockMvc.perform(get("/api/samples/barcode/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new SampleCreateRequest("BC-001", "Serum", "P-100", "STU-2025",
                LocalDate.of(2025, 1, 15), "1:10 dilution", "Fast sample");
        when(sampleService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.barcode").value("BC-001"));
    }

    @Test
    void create_shouldReturn400_whenBarcodeBlank() throws Exception {
        var request = new SampleCreateRequest("", null, null, null, null, null, null);

        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_shouldReturnUpdatedSample() throws Exception {
        var request = new SampleUpdateRequest("Plasma", null, null, null, null, "Updated note");
        var updated = SampleResponse.builder()
                .id(1L).barcode("BC-001").matrixType("Plasma").notes("Updated note").build();
        when(sampleService.update(eq(1L), any(SampleUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/samples/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrixType").value("Plasma"))
                .andExpect(jsonPath("$.notes").value("Updated note"));
    }

    @Test
    void update_shouldReturn404_whenNotFound() throws Exception {
        var request = new SampleUpdateRequest(null, null, null, null, null, null);
        when(sampleService.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Sample not found with id: 99"));

        mockMvc.perform(put("/api/samples/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}

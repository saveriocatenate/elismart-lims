package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.dto.ReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.ReagentBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link ReagentBatchController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ReagentBatchController.class)
class ReagentBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReagentBatchService reagentBatchService;

    private final ReagentBatchResponse response = ReagentBatchResponse.builder()
            .id(1L)
            .reagentId(10L)
            .lotNumber("LOT-001")
            .expiryDate(LocalDate.of(2026, 12, 31))
            .supplier("SupplierX")
            .notes("Keep refrigerated")
            .build();

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new ReagentBatchCreateRequest(
                10L, "LOT-001", LocalDate.of(2026, 12, 31), "SupplierX", "Keep refrigerated");
        when(reagentBatchService.create(any(ReagentBatchCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/reagent-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lotNumber").value("LOT-001"))
                .andExpect(jsonPath("$.reagentId").value(10));
    }

    @Test
    void create_shouldReturn400_whenRequestInvalid() throws Exception {
        // Missing required lotNumber
        var invalid = new ReagentBatchCreateRequest(10L, "", LocalDate.of(2026, 12, 31), null, null);

        mockMvc.perform(post("/api/reagent-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listByReagent_shouldReturnList_whenReagentIdProvided() throws Exception {
        when(reagentBatchService.findByReagentId(10L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/reagent-batches").param("reagentId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].lotNumber").value("LOT-001"));
    }

    @Test
    void listByReagent_shouldReturnEmptyList_whenNoReagentIdProvided() throws Exception {
        mockMvc.perform(get("/api/reagent-batches"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getById_shouldReturnBatch() throws Exception {
        when(reagentBatchService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/reagent-batches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lotNumber").value("LOT-001"));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(reagentBatchService.findById(99L)).thenThrow(new ResourceNotFoundException("ReagentBatch not found with id: 99"));

        mockMvc.perform(get("/api/reagent-batches/99"))
                .andExpect(status().isNotFound());
    }
}

package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.ExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ExperimentController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ExperimentController.class)
class ExperimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExperimentService experimentService;

    private ExperimentResponse sampleResponse() {
        return ExperimentResponse.builder()
                .id(1L)
                .name("Run 2026-04-05")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.OK)
                .protocolName("IgG Test")
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();
    }

    @Test
    void getById_shouldReturnExperiment() throws Exception {
        when(experimentService.getById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/experiments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Run 2026-04-05"));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(experimentService.getById(1L)).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/experiments/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new ExperimentRequest(
                "Run 2026-04-05",
                LocalDateTime.of(2026, 4, 5, 10, 0),
                1L,
                ExperimentStatus.OK,
                List.of(new UsedReagentBatchRequest(1L, "LOT-001", null)),
                List.of());
        when(experimentService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void create_shouldReturn400_whenMissingReagents() throws Exception {
        var request = new ExperimentRequest("Run", LocalDateTime.of(2026, 4, 5, 10, 0), 1L, ExperimentStatus.OK,
                List.of(new UsedReagentBatchRequest(1L, "LOT-001", null)), List.of());
        when(experimentService.create(any())).thenThrow(new ProtocolMismatchException("Missing reagents"));

        mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/experiments/1"))
                .andExpect(status().isNoContent());
        verify(experimentService).delete(1L);
    }

    @Test
    void delete_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Not found")).when(experimentService).delete(1L);

        mockMvc.perform(delete("/api/experiments/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn400_whenMeasurementPairMissingSignal1() throws Exception {
        // signal1 is absent — @NotNull on MeasurementPairRequest.signal1 must trigger a 400
        String json = """
                {
                  "name": "Run X",
                  "date": "2026-04-05T10:00:00",
                  "protocolId": 1,
                  "status": "PENDING",
                  "usedReagentBatches": [{"reagentId": 1, "lotNumber": "LOT-001", "expiryDate": null}],
                  "measurementPairs": [
                    {"pairType": "CALIBRATION", "signal2": 0.47, "recoveryPct": null, "isOutlier": false}
                  ]
                }
                """;

        mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400_whenMeasurementPairMissingSignal2() throws Exception {
        // signal2 is absent — @NotNull on MeasurementPairRequest.signal2 must trigger a 400
        String json = """
                {
                  "name": "Run X",
                  "date": "2026-04-05T10:00:00",
                  "protocolId": 1,
                  "status": "PENDING",
                  "usedReagentBatches": [{"reagentId": 1, "lotNumber": "LOT-001", "expiryDate": null}],
                  "measurementPairs": [
                    {"pairType": "CALIBRATION", "signal1": 0.45, "recoveryPct": null, "isOutlier": false}
                  ]
                }
                """;

        mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_shouldReturnPaginatedResults() throws Exception {
        var searchRequest = new ExperimentSearchRequest(
                null, null, null, null, null, 0, 20);
        var page = new ExperimentPage(
                List.of(sampleResponse()), 0, 20, 1, 1, true);
        when(experimentService.search(any())).thenReturn(page);

        mockMvc.perform(post("/api/experiments/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }
}

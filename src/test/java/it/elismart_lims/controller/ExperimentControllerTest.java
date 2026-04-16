package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.CsvFormat;
import it.elismart_lims.dto.CsvImportConfig;
import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.WellMapping;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.service.ExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                List.of(new UsedReagentBatchRequest(1L)),
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
                List.of(new UsedReagentBatchRequest(1L)), List.of());
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
                  "usedReagentBatches": [{"reagentBatchId": 1}],
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
                  "usedReagentBatches": [{"reagentBatchId": 1}],
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
                null, null, null, null, null, 0, 20, false);
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

    @Test
    void validate_shouldReturn200_withUpdatedExperiment() throws Exception {
        when(experimentService.validate(1L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/experiments/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OK"));

        verify(experimentService).validate(1L);
    }

    @Test
    void validate_shouldReturn404_whenExperimentNotFound() throws Exception {
        when(experimentService.validate(99L)).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(post("/api/experiments/99/validate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validate_shouldReturn409_whenExperimentAlreadyTerminal() throws Exception {
        when(experimentService.validate(1L)).thenThrow(
                new IllegalStateException("Experiment id=1 is already in terminal status OK."));

        mockMvc.perform(post("/api/experiments/1/validate"))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // POST /{id}/import-csv
    // -------------------------------------------------------------------------

    /**
     * A valid multipart request with a non-empty CSV and a well-formed config
     * must return 200 with the updated experiment response.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("importCsv returns 200 with updated experiment")
    void importCsv_shouldReturn200_withUpdatedExperiment() throws Exception {
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2",
                Map.of("A1", new WellMapping(PairType.CALIBRATION, 0.5)));

        MockMultipartFile filePart = new MockMultipartFile(
                "file", "data.csv", "text/csv",
                "WellId,Signal1,Signal2\nA1,0.45,0.47".getBytes());
        MockMultipartFile configPart = new MockMultipartFile(
                "config", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(config));

        when(experimentService.importCsv(eq(1L), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(multipart("/api/experiments/1/import-csv")
                        .file(filePart)
                        .file(configPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(experimentService).importCsv(eq(1L), any(), any());
    }

    /**
     * When the service throws {@link IllegalArgumentException} (e.g. empty file),
     * the endpoint must return HTTP 400.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("importCsv returns 400 when service throws IllegalArgumentException")
    void importCsv_shouldReturn400_whenServiceThrowsIllegalArgument() throws Exception {
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2", Map.of());

        MockMultipartFile filePart = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);
        MockMultipartFile configPart = new MockMultipartFile(
                "config", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(config));

        when(experimentService.importCsv(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("CSV file must not be empty."));

        mockMvc.perform(multipart("/api/experiments/1/import-csv")
                        .file(filePart)
                        .file(configPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file must not be empty."));
    }

    /**
     * When the experiment does not exist, the endpoint must return HTTP 404.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("importCsv returns 404 when experiment not found")
    void importCsv_shouldReturn404_whenExperimentNotFound() throws Exception {
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2", Map.of());

        MockMultipartFile filePart = new MockMultipartFile(
                "file", "data.csv", "text/csv",
                "WellId,Signal1,Signal2\nA1,0.45,0.47".getBytes());
        MockMultipartFile configPart = new MockMultipartFile(
                "config", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(config));

        when(experimentService.importCsv(eq(99L), any(), any()))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        mockMvc.perform(multipart("/api/experiments/99/import-csv")
                        .file(filePart)
                        .file(configPart))
                .andExpect(status().isNotFound());
    }
}

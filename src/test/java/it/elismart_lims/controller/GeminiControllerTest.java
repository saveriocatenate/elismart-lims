package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link GeminiController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(GeminiController.class)
class GeminiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiService geminiService;

    /**
     * POST /api/ai/analyze returns 200 with the analysis text when the service succeeds.
     */
    @Test
    void analyze_returns200_whenServiceSucceeds() throws Exception {
        GeminiAnalysisResponse serviceResponse = GeminiAnalysisResponse.builder()
                .analysis("AI analysis result")
                .build();
        when(geminiService.analyze(any(GeminiAnalysisRequest.class))).thenReturn(serviceResponse);

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(
                List.of(1L, 2L), "Why did the control fail?");

        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").value("AI analysis result"));
    }

    /**
     * POST /api/ai/analyze returns 404 when the service throws ResourceNotFoundException.
     */
    @Test
    void analyze_returns404_whenExperimentNotFound() throws Exception {
        when(geminiService.analyze(any(GeminiAnalysisRequest.class)))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(
                List.of(99L), "Any question");

        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}

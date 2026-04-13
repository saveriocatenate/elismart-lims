package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.AiInsightResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.AiInsightService;
import it.elismart_lims.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private AiInsightService aiInsightService;

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

    /**
     * GET /api/ai/insights?experimentId=1 returns 200 with the list of persisted insights.
     */
    @Test
    void getInsights_returns200_withInsightList() throws Exception {
        AiInsightResponse insight = AiInsightResponse.builder()
                .id(1L)
                .userQuestion("Why did the control fail?")
                .aiResponse("The control failed due to reagent degradation.")
                .generatedAt(LocalDateTime.of(2026, 4, 13, 10, 0))
                .generatedBy("analyst1")
                .experimentIds(List.of(1L))
                .build();

        when(aiInsightService.getByExperimentId(eq(1L))).thenReturn(List.of(insight));

        mockMvc.perform(get("/api/ai/insights").param("experimentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userQuestion").value("Why did the control fail?"))
                .andExpect(jsonPath("$[0].generatedBy").value("analyst1"));
    }

    /**
     * GET /api/ai/insights?experimentId=99 returns 200 with an empty array when no insights exist.
     */
    @Test
    void getInsights_returns200_withEmptyList_whenNoInsightsExist() throws Exception {
        when(aiInsightService.getByExperimentId(eq(99L))).thenReturn(List.of());

        mockMvc.perform(get("/api/ai/insights").param("experimentId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}

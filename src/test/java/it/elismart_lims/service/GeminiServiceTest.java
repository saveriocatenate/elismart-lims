package it.elismart_lims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeminiService}.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ProtocolService protocolService;

    private GeminiService geminiService;

    private static final String FAKE_API_KEY = "test-key";
    private static final String GEMINI_JSON_RESPONSE = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text": "AI analysis result"}],
                  "role": "model"
                },
                "finishReason": "STOP"
              }]
            }
            """;

    private ExperimentResponse sampleExperiment;
    private ProtocolResponse sampleProtocol;

    @BeforeEach
    void setUp() {
        sampleProtocol = ProtocolResponse.builder()
                .id(1L)
                .name("ELISA Dose-Response")
                .numCalibrationPairs(8)
                .numControlPairs(2)
                .maxCvAllowed(15.0)
                .maxErrorAllowed(20.0)
                .build();

        MeasurementPairResponse calPair = MeasurementPairResponse.builder()
                .id(1L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(1.0)
                .signal1(0.5)
                .signal2(0.51)
                .signalMean(0.505)
                .cvPct(1.4)
                .recoveryPct(100.5)
                .isOutlier(false)
                .build();

        MeasurementPairResponse ctrlPair = MeasurementPairResponse.builder()
                .id(2L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(5.0)
                .signal1(1.0)
                .signal2(1.02)
                .signalMean(1.01)
                .cvPct(1.4)
                .recoveryPct(101.0)
                .isOutlier(false)
                .build();

        UsedReagentBatchResponse batch = UsedReagentBatchResponse.builder()
                .id(1L)
                .reagentName("Capture Ab")
                .lotNumber("A1")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .build();

        sampleExperiment = ExperimentResponse.builder()
                .id(1L)
                .name("Exp 2026-04-01")
                .date(LocalDateTime.of(2026, 4, 1, 9, 0))
                .status(ExperimentStatus.OK)
                .protocolName("ELISA Dose-Response")
                .usedReagentBatches(List.of(batch))
                .measurementPairs(List.of(calPair, ctrlPair))
                .build();
    }

    /**
     * Verifies that analyze() returns the AI text extracted from the Gemini JSON response
     * when all dependencies resolve correctly. The HTTP call is intercepted via a spy on the
     * package-private {@link GeminiService#callGeminiApi} method.
     */
    @Test
    void analyze_returnsAnalysisText_whenGeminiRespondsSuccessfully() {
        // given
        geminiService = spy(buildService(mock(RestClient.class, RETURNS_DEEP_STUBS)));
        doReturn(GEMINI_JSON_RESPONSE).when(geminiService).callGeminiApi(anyString());

        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        ExperimentResponse exp2 = ExperimentResponse.builder()
                .id(2L)
                .name("Exp 2026-04-02")
                .date(LocalDateTime.of(2026, 4, 2, 9, 0))
                .status(ExperimentStatus.KO)
                .protocolName("ELISA Dose-Response")
                .usedReagentBatches(sampleExperiment.usedReagentBatches())
                .measurementPairs(sampleExperiment.measurementPairs())
                .build();
        when(experimentService.getById(2L)).thenReturn(exp2);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(1L, 2L), "Why did the control fail?");

        // when
        GeminiAnalysisResponse response = geminiService.analyze(request);

        // then
        assertThat(response.analysis()).isEqualTo("AI analysis result");
    }

    /**
     * Verifies that analyze() propagates ResourceNotFoundException when an experiment ID is invalid.
     */
    @Test
    void analyze_throwsResourceNotFoundException_whenExperimentNotFound() {
        // given
        RestClient mockRestClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        geminiService = buildService(mockRestClient);

        when(experimentService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(99L), "Some question");

        // when / then
        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    /**
     * Verifies that analyze() throws IllegalArgumentException when no experiment IDs are given.
     */
    @Test
    void analyze_throwsIllegalArgumentException_whenNoExperimentIds() {
        // given
        RestClient mockRestClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        geminiService = buildService(mockRestClient);

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(), "Any question");

        // when / then
        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link GeminiService} instance with the given mock RestClient injected via the
     * RestClient.Builder — the builder is pre-configured to return the mock when built.
     *
     * @param mockRestClient the mock RestClient to use for HTTP calls
     * @return a configured GeminiService
     */
    private GeminiService buildService(RestClient mockRestClient) {
        RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mockRestClient);

        return new GeminiService(
                FAKE_API_KEY,
                "https://generativelanguage.googleapis.com",
                "gemini-1.5-flash",
                builder,
                new ObjectMapper(),
                experimentService,
                protocolService);
    }
}

package it.elismart_lims.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.GeminiServiceException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeminiService}.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ProtocolService protocolService;

    private GeminiService geminiService;

    private ExperimentResponse sampleExperiment;
    private ProtocolResponse sampleProtocol;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService(chatLanguageModel, experimentService, protocolService);

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
     * Verifies that analyze() returns the AI text from the ChatLanguageModel response
     * when all dependencies resolve correctly.
     */
    @Test
    void analyze_returnsAnalysisText_whenGeminiRespondsSuccessfully() {
        // given
        when(chatLanguageModel.generate(anyString())).thenReturn("AI analysis result");

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
        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(), "Any question");

        // when / then
        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that a 401 HTTP error from LangChain4j is mapped to a GeminiServiceException
     * with httpStatus=401 and an appropriate message.
     */
    @Test
    void analyze_throwsGeminiServiceException_withStatus401_onAuthFailure() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException(
                        "HTTP error (401): {\"error\":{\"code\":401,\"message\":\"API key not valid\",\"status\":\"UNAUTHENTICATED\"}}"));

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(1L), "Test question");

        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Invalid or missing Gemini API key")
                .satisfies(ex -> assertThat(((GeminiServiceException) ex).getHttpStatus()).isEqualTo(401));
    }

    /**
     * Verifies that an HttpTimeoutException cause is mapped to a GeminiServiceException
     * with httpStatus=504 and an appropriate message.
     */
    @Test
    void analyze_throwsGeminiServiceException_withStatus504_onTimeout() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException(
                        "An error occurred while sending the request",
                        new java.net.http.HttpTimeoutException("request timed out")));

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(1L), "Test question");

        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Gemini API request timed out")
                .satisfies(ex -> assertThat(((GeminiServiceException) ex).getHttpStatus()).isEqualTo(504));
    }

    /**
     * Verifies that a transient failure (timeout) followed by a success on the third attempt
     * returns the successful response without propagating any exception.
     */
    @Test
    void analyze_retriesOnTimeout_andSucceedsOnThirdAttempt() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        RuntimeException timeout = new RuntimeException(
                "An error occurred while sending the request", new HttpTimeoutException("timed out"));
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(timeout)
                .thenThrow(timeout)
                .thenReturn("AI analysis result");

        GeminiService spied = spy(geminiService);
        doNothing().when(spied).sleep(anyLong());

        GeminiAnalysisResponse response = spied.analyze(new GeminiAnalysisRequest(List.of(1L), "Test?"));

        assertThat(response.analysis()).isEqualTo("AI analysis result");
        verify(chatLanguageModel, times(3)).generate(anyString());
        verify(spied, times(2)).sleep(anyLong()); // slept after attempt 1 and attempt 2
    }

    /**
     * Verifies that three consecutive timeouts exhaust all retries and throw
     * a GeminiServiceException with httpStatus=504.
     */
    @Test
    void analyze_throwsGeminiServiceException_afterExhaustingRetries_onThreeTimeouts() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        RuntimeException timeout = new RuntimeException(
                "An error occurred while sending the request", new HttpTimeoutException("timed out"));
        when(chatLanguageModel.generate(anyString())).thenThrow(timeout);

        GeminiService spied = spy(geminiService);
        doNothing().when(spied).sleep(anyLong());

        assertThatThrownBy(() -> spied.analyze(new GeminiAnalysisRequest(List.of(1L), "Test?")))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("timed out")
                .satisfies(ex -> assertThat(((GeminiServiceException) ex).getHttpStatus()).isEqualTo(504));

        verify(chatLanguageModel, times(3)).generate(anyString());
        verify(spied, times(2)).sleep(anyLong()); // no sleep after the last (3rd) attempt
    }

    /**
     * Verifies that an auth failure (401) on the first attempt causes an immediate throw
     * with no retry — {@code generate()} must be called exactly once.
     */
    @Test
    void analyze_throwsImmediately_withoutRetry_onAuthFailure() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException(
                        "HTTP error (401): {\"error\":{\"code\":401,\"message\":\"API key not valid\"}}"));

        GeminiService spied = spy(geminiService);

        assertThatThrownBy(() -> spied.analyze(new GeminiAnalysisRequest(List.of(1L), "Test?")))
                .isInstanceOf(GeminiServiceException.class)
                .satisfies(ex -> assertThat(((GeminiServiceException) ex).getHttpStatus()).isEqualTo(401));

        verify(chatLanguageModel, times(1)).generate(anyString()); // called exactly once — no retry
        verify(spied, times(0)).sleep(anyLong());                  // never slept
    }

    /**
     * Verifies that a 429 HTTP error from LangChain4j is mapped to a GeminiServiceException
     * with httpStatus=429.
     */
    @Test
    void analyze_throwsGeminiServiceException_withStatus429_onRateLimit() {
        when(experimentService.getById(1L)).thenReturn(sampleExperiment);
        when(protocolService.getByName("ELISA Dose-Response")).thenReturn(sampleProtocol);
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("HTTP error (429): {\"error\":{\"code\":429,\"message\":\"Resource exhausted\"}}"));

        GeminiAnalysisRequest request = new GeminiAnalysisRequest(List.of(1L), "Test question");

        assertThatThrownBy(() -> geminiService.analyze(request))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Gemini API rate limit exceeded")
                .satisfies(ex -> assertThat(((GeminiServiceException) ex).getHttpStatus()).isEqualTo(429));
    }
}

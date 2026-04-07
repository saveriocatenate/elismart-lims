package it.elismart_lims.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.GeminiServiceException;
import it.elismart_lims.model.PairType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Service that builds a structured prompt from experiment data and queries the Google Gemini API
 * to produce a quantitative and qualitative analysis.
 *
 * <p>The prompt follows a three-section structure:
 * {@code [SYSTEM_CONTEXT]}, {@code [EXPERIMENT_DATA]}, and {@code [USER_QUERY]}.</p>
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code gemini.api-key} — Google Gemini API key (required for live calls)</li>
 *   <li>{@code gemini.base-url} — Gemini API base URL</li>
 *   <li>{@code gemini.model} — Gemini model name</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class GeminiService {

    /** Relative path template filled in from the configured model name. */
    private final String geminiPath;

    /** Google Gemini API key injected from {@code gemini.api-key}. */
    private final String apiKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExperimentService experimentService;
    private final ProtocolService protocolService;

    /**
     * Constructs the GeminiService, building a dedicated {@link RestClient} pointed at the
     * Gemini base URL with configured connect and read timeouts.
     *
     * @param apiKey             the Google Gemini API key, injected from {@code gemini.api-key}
     * @param baseUrl            the Gemini API base URL, injected from {@code gemini.base-url}
     * @param model              the Gemini model identifier, injected from {@code gemini.model}
     * @param restClientBuilder  the Spring-managed RestClient builder
     * @param objectMapper       Jackson ObjectMapper for response parsing
     * @param experimentService  service used to fetch experiment data
     * @param protocolService    service used to fetch protocol limits by name
     */
    public GeminiService(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.model}") String model,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            ExperimentService experimentService,
            ProtocolService protocolService) {
        this.apiKey = apiKey;
        this.geminiPath = "/v1beta/models/" + model + ":generateContent";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.experimentService = experimentService;
        this.protocolService = protocolService;
    }

    /**
     * Fetch each experiment, build a structured prompt, call the Gemini API, and return the
     * analysis text.
     *
     * @param request contains the list of experiment IDs and the user's question
     * @return a {@link GeminiAnalysisResponse} with the AI-generated text
     * @throws IllegalArgumentException if no experiment IDs are provided
     * @throws GeminiServiceException   if the Gemini API call fails or returns an unexpected response
     */
    public GeminiAnalysisResponse analyze(GeminiAnalysisRequest request) {
        if (request.experimentIds() == null || request.experimentIds().isEmpty()) {
            throw new IllegalArgumentException("At least one experiment ID is required.");
        }

        List<ExperimentResponse> experiments = request.experimentIds().stream()
                .map(experimentService::getById)
                .toList();

        log.info("Running Gemini analysis for {} experiment(s): {}",
                experiments.size(), request.experimentIds());

        String protocolName = experiments.getFirst().protocolName();
        ProtocolResponse protocol = protocolService.getByName(protocolName);

        String prompt = buildPrompt(experiments, protocol, request.userQuestion());
        log.debug("Gemini prompt built ({} chars)", prompt.length());

        String rawJson = callGeminiApi(prompt);
        String analysisText = extractText(rawJson);

        log.info("Gemini analysis completed successfully for experiments: {}", request.experimentIds());
        return GeminiAnalysisResponse.builder().analysis(analysisText).build();
    }

    // -------------------------------------------------------------------------
    // Prompt building
    // -------------------------------------------------------------------------

    /**
     * Builds the three-section prompt: SYSTEM_CONTEXT, EXPERIMENT_DATA, USER_QUERY.
     *
     * @param experiments  the fetched experiment DTOs
     * @param protocol     the associated protocol with its limits
     * @param userQuestion the analyst's free-text question
     * @return the complete prompt string
     */
    private String buildPrompt(
            List<ExperimentResponse> experiments,
            ProtocolResponse protocol,
            String userQuestion) {

        String systemContext = buildSystemContext(experiments.size(), protocol);
        String experimentData = buildExperimentData(experiments);

        return "[SYSTEM_CONTEXT]\n" + systemContext
                + "\n\n[EXPERIMENT_DATA]\n" + experimentData
                + "\n\n[USER_QUERY]\n" + userQuestion;
    }

    /**
     * Builds the SYSTEM_CONTEXT block describing the analyst role and protocol limits.
     *
     * @param count    number of experiments being analyzed
     * @param protocol the protocol whose limits apply
     * @return the SYSTEM_CONTEXT string
     */
    private String buildSystemContext(int count, ProtocolResponse protocol) {
        return String.format(
                "You are a senior Biotech Analyst for EliSmart. " +
                "Analyze the following %d ELISA experiment(s) run under the \"%s\" protocol.\n" +
                "Protocol Limits: Max %%CV: %.1f%% | Max %%Error: %.1f%%.",
                count,
                protocol.name(),
                protocol.maxCvAllowed(),
                protocol.maxErrorAllowed());
    }

    /**
     * Builds the EXPERIMENT_DATA block with a concise summary of each experiment.
     *
     * @param experiments the fetched experiment DTOs
     * @return the EXPERIMENT_DATA string
     */
    private String buildExperimentData(List<ExperimentResponse> experiments) {
        StringBuilder sb = new StringBuilder();
        for (ExperimentResponse exp : experiments) {
            sb.append(formatExperiment(exp)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Formats a single experiment into a concise multi-line summary.
     *
     * @param exp the experiment DTO
     * @return the formatted experiment string
     */
    private String formatExperiment(ExperimentResponse exp) {
        StringBuilder sb = new StringBuilder();
        String date = exp.date() != null ? exp.date().toLocalDate().toString() : "unknown date";
        sb.append(String.format("Exp %d \"%s\" (%s): Status %s.",
                exp.id(), exp.name(), date, exp.status()));

        // Reagent lots
        List<UsedReagentBatchResponse> batches = exp.usedReagentBatches();
        if (batches != null && !batches.isEmpty()) {
            String lots = batches.stream()
                    .map(b -> b.reagentName() + "=" + b.lotNumber())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("—");
            sb.append(" Reagent Lots: [").append(lots).append("].");
        }

        // Calibration pairs
        List<MeasurementPairResponse> calPairs = filterPairs(exp, PairType.CALIBRATION);
        sb.append(formatPairsSummary("Calibration", calPairs));

        // Control pairs
        List<MeasurementPairResponse> ctrlPairs = filterPairs(exp, PairType.CONTROL);
        sb.append(formatPairsSummary("Controls", ctrlPairs));

        return sb.toString();
    }

    /**
     * Filters measurement pairs by type.
     *
     * @param exp      the experiment DTO
     * @param pairType the pair type to filter by
     * @return the filtered list, empty if no pairs are recorded
     */
    private List<MeasurementPairResponse> filterPairs(ExperimentResponse exp, PairType pairType) {
        if (exp.measurementPairs() == null) {
            return List.of();
        }
        return exp.measurementPairs().stream()
                .filter(p -> pairType.equals(p.pairType()))
                .toList();
    }

    /**
     * Produces a one-line summary of a set of measurement pairs.
     *
     * @param label the human-readable label (e.g. "Calibration")
     * @param pairs the measurement pair DTOs
     * @return the formatted summary string, empty if no pairs
     */
    private String formatPairsSummary(String label, List<MeasurementPairResponse> pairs) {
        if (pairs.isEmpty()) {
            return "";
        }
        OptionalDouble avgCv = pairs.stream()
                .filter(p -> p.cvPct() != null)
                .mapToDouble(MeasurementPairResponse::cvPct)
                .average();
        OptionalDouble avgRec = pairs.stream()
                .filter(p -> p.recoveryPct() != null)
                .mapToDouble(MeasurementPairResponse::recoveryPct)
                .average();

        String cvStr = avgCv.isPresent() ? String.format("%.1f%%", avgCv.getAsDouble()) : "n/a";
        String recStr = avgRec.isPresent() ? String.format("%.1f%%", avgRec.getAsDouble()) : "n/a";
        return String.format(" %s (%d pairs): Avg %%CV=%s | Avg %%Rec=%s.", label, pairs.size(), cvStr, recStr);
    }

    // -------------------------------------------------------------------------
    // HTTP call
    // -------------------------------------------------------------------------

    /**
     * Posts the prompt to the Gemini generateContent endpoint and returns the raw JSON response.
     * Package-private to allow spy-based testing without a live network call.
     *
     * @param prompt the complete prompt string
     * @return the raw JSON string from the Gemini API
     */
    String callGeminiApi(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        return restClient.post()
                .uri(geminiPath + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * Extracts the generated text from the Gemini API JSON response.
     *
     * <p>Expected structure: {@code candidates[0].content.parts[0].text}</p>
     *
     * @param rawJson the raw JSON string from the Gemini API
     * @return the extracted text
     * @throws GeminiServiceException if parsing fails or the expected path is absent
     */
    private String extractText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            return root.at("/candidates/0/content/parts/0/text").asText();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", rawJson, e);
            throw new GeminiServiceException("Failed to parse Gemini API response", e);
        }
    }
}

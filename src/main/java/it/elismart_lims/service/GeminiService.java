package it.elismart_lims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.GeminiServiceException;
import it.elismart_lims.model.PairType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Service that builds a structured prompt from experiment data and queries the Google Gemini API
 * via LangChain4j's {@link ChatLanguageModel} to produce a quantitative and qualitative analysis.
 *
 * <p>The prompt follows a three-section structure:
 * {@code [SYSTEM_CONTEXT]}, {@code [EXPERIMENT_DATA]}, and {@code [USER_QUERY]}.</p>
 *
 * <p>After a successful response from the model, the question and answer are persisted via
 * {@link AiInsightService} so that the frontend can display past analyses without re-running them.</p>
 *
 * <p>If all requested experiments contain zero measurement pairs the service returns a user-facing
 * message without calling the API, avoiding unnecessary quota consumption.</p>
 *
 * <p>The {@link ChatLanguageModel} bean is configured in {@link it.elismart_lims.config.GeminiConfig}
 * from the following properties:
 * <ul>
 *   <li>{@code gemini.api-key} — Google Gemini API key</li>
 *   <li>{@code gemini.model} — Gemini model name</li>
 *   <li>{@code gemini.timeout-ms} — HTTP read timeout in milliseconds (default: 120000)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    /** Maximum number of attempts before giving up. */
    private static final int MAX_RETRIES = 3;

    /** Base delay in milliseconds; doubles on each subsequent attempt (1 s → 2 s → 4 s). */
    private static final long INITIAL_DELAY_MS = 1000;

    private final ChatLanguageModel chatLanguageModel;
    private final ExperimentService experimentService;
    private final ProtocolService protocolService;
    private final AiInsightService aiInsightService;
    private final ObjectMapper objectMapper;

    /**
     * Fetches each experiment, builds a structured prompt, calls the Gemini API via LangChain4j,
     * and returns the analysis text.
     *
     * @param request contains the list of experiment IDs and the user's question
     * @return a {@link GeminiAnalysisResponse} with the AI-generated text
     * @throws IllegalArgumentException if no experiment IDs are provided
     * @throws GeminiServiceException   if the Gemini API call fails
     */
    public GeminiAnalysisResponse analyze(GeminiAnalysisRequest request) {
        if (request.experimentIds() == null || request.experimentIds().isEmpty()) {
            throw new IllegalArgumentException("At least one experiment ID is required.");
        }

        List<ExperimentResponse> experiments = request.experimentIds().stream()
                .map(experimentService::getById)
                .toList();

        long totalPairs = experiments.stream()
                .mapToLong(e -> e.measurementPairs() == null ? 0 : e.measurementPairs().size())
                .sum();
        if (totalPairs == 0) {
            log.warn("AI analysis aborted — no measurement pairs found for experiments: {}",
                    request.experimentIds());
            return GeminiAnalysisResponse.builder()
                    .analysis("L'esperimento non contiene dati di misurazione. "
                            + "L'analisi AI richiede almeno una coppia di misurazioni.")
                    .build();
        }

        log.info("Running Gemini analysis for {} experiment(s): {}",
                experiments.size(), request.experimentIds());

        String protocolName = experiments.getFirst().protocolName();
        ProtocolResponse protocol = protocolService.getByName(protocolName);

        String prompt = buildPrompt(experiments, protocol, request.userQuestion());
        log.debug("Gemini request ({} chars):\n{}", prompt.length(), prompt);

        String analysisText = callWithRetry(prompt);
        log.debug("Gemini response:\n{}", analysisText);
        log.info("Gemini analysis completed successfully for experiments: {}", request.experimentIds());

        String currentUser = resolveCurrentUsername();
        aiInsightService.save(request.userQuestion(), analysisText, currentUser, request.experimentIds());

        return GeminiAnalysisResponse.builder().analysis(analysisText).build();
    }

    // -------------------------------------------------------------------------
    // Security context helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the username of the currently authenticated principal, or {@code "anonymous"}
     * if no authentication is present in the security context.
     *
     * <p>Declared {@code protected} so that unit tests can override it via a Mockito spy.</p>
     *
     * @return the authenticated username
     */
    protected String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    // -------------------------------------------------------------------------
    // Retry logic
    // -------------------------------------------------------------------------

    /**
     * Calls {@code ChatLanguageModel.generate()} with exponential-backoff retry.
     *
     * <p>Only transient errors (timeout, rate limit, 5xx) are retried. Non-transient
     * errors (e.g. 401 auth failure) cause an immediate throw without any retry.</p>
     *
     * <p>Backoff schedule (with {@code INITIAL_DELAY_MS = 1000}):
     * attempt 1 → sleep 1 s → attempt 2 → sleep 2 s → attempt 3 → throw.</p>
     *
     * @param prompt the fully-assembled prompt string
     * @return the raw text returned by the model
     * @throws GeminiServiceException if all retries are exhausted or a non-transient error occurs
     */
    private String callWithRetry(String prompt) {
        GeminiServiceException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return chatLanguageModel.generate(prompt);
            } catch (RuntimeException e) {
                GeminiServiceException classified = classifyException(e);

                if (!isTransient(classified)) {
                    log.error("Non-transient Gemini API error (attempt {}/{}), not retrying: {}",
                            attempt, MAX_RETRIES, e.getMessage());
                    throw classified;
                }

                lastException = classified;

                if (attempt < MAX_RETRIES) {
                    long delayMs = INITIAL_DELAY_MS * (1L << (attempt - 1)); // 1 s, 2 s, 4 s …
                    log.warn("Transient Gemini API error (attempt {}/{}), retrying in {} ms: {}",
                            attempt, MAX_RETRIES, delayMs, e.getMessage());
                    sleep(delayMs);
                } else {
                    log.error("Gemini API call failed after {} attempt(s): {}", MAX_RETRIES, e.getMessage(), e);
                }
            }
        }

        throw lastException; // always non-null: only reachable after a transient failure
    }

    /**
     * Returns {@code true} if the classified exception represents a transient failure
     * that is safe to retry (rate limit, timeout, or any 5xx server error).
     *
     * @param ex the classified exception
     * @return {@code true} if a retry is appropriate
     */
    private boolean isTransient(GeminiServiceException ex) {
        int status = ex.getHttpStatus();
        return status == 429 || status >= 500;
    }

    /**
     * Sleeps for the given number of milliseconds.
     *
     * <p>Declared {@code protected} so that tests can override it via a Mockito spy
     * to avoid actual wall-clock delays.</p>
     *
     * @param ms milliseconds to sleep
     * @throws RuntimeException if the thread is interrupted while sleeping
     */
    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", ie);
        }
    }

    // -------------------------------------------------------------------------
    // Exception classification
    // -------------------------------------------------------------------------

    /**
     * Maps a raw {@link RuntimeException} from LangChain4j to a typed
     * {@link GeminiServiceException} with the appropriate HTTP status code.
     *
     * <p>LangChain4j's Google-AI Gemini integration wraps all failures in
     * {@code RuntimeException}. The message follows the pattern
     * {@code "HTTP error (NNN): ..."} for HTTP-level errors, and the cause
     * chain carries a {@link java.net.http.HttpTimeoutException} for timeouts.</p>
     *
     * @param e the raw exception thrown by {@code ChatLanguageModel.generate()}
     * @return a {@link GeminiServiceException} with the correct HTTP status
     */
    private GeminiServiceException classifyException(RuntimeException e) {
        String msg = e.getMessage();

        if (msg != null && msg.contains("HTTP error (401)")) {
            return new GeminiServiceException("Invalid or missing Gemini API key", e, 401);
        }

        if (msg != null && msg.contains("HTTP error (429)")) {
            return new GeminiServiceException("Gemini API rate limit exceeded", e, 429);
        }

        Throwable cause = e.getCause();
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new GeminiServiceException("Gemini API request timed out", e, 504);
        }

        return new GeminiServiceException("Failed to get response from Gemini API", e, 502);
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
        String experimentData = buildExperimentData(experiments, protocol.maxCvAllowed(), protocol.maxErrorAllowed());

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
        return String.format(Locale.ROOT,
                "You are a senior Biotech Analyst for EliSmart. " +
                "Analyze the following %d laboratory assay experiment(s) run under the \"%s\" protocol.\n" +
                "Protocol Limits: Max %%CV: %.1f%% | Max %%Error: %.1f%%.",
                count,
                protocol.name(),
                protocol.maxCvAllowed(),
                protocol.maxErrorAllowed());
    }

    /**
     * Builds the EXPERIMENT_DATA block with a detailed summary of each experiment.
     *
     * @param experiments    the fetched experiment DTOs
     * @param maxCvAllowed   protocol %CV upper limit, used to flag violations per pair
     * @param maxErrorAllowed protocol %Error upper limit, used to flag %Recovery violations
     * @return the EXPERIMENT_DATA string
     */
    private String buildExperimentData(List<ExperimentResponse> experiments,
                                        double maxCvAllowed, double maxErrorAllowed) {
        StringBuilder sb = new StringBuilder();
        for (ExperimentResponse exp : experiments) {
            sb.append(formatExperiment(exp, maxCvAllowed, maxErrorAllowed)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Formats a single experiment into a detailed multi-line summary including
     * per-pair signal values, outlier flags, pair status, and curve fit metrics.
     *
     * @param exp             the experiment DTO
     * @param maxCvAllowed    protocol %CV limit for per-pair violation markers
     * @param maxErrorAllowed protocol %Error limit for per-pair %Recovery violation markers
     * @return the formatted experiment string
     */
    private String formatExperiment(ExperimentResponse exp, double maxCvAllowed, double maxErrorAllowed) {
        StringBuilder sb = new StringBuilder();
        String date = exp.date() != null ? exp.date().toLocalDate().toString() : "unknown date";
        sb.append(String.format(Locale.ROOT, "Exp %d \"%s\" (%s): Status %s.",
                exp.id(), exp.name(), date, exp.status()));

        // Reagent lots
        List<UsedReagentBatchResponse> batches = exp.usedReagentBatches();
        if (batches != null && !batches.isEmpty()) {
            String lots = batches.stream()
                    .map(b -> b.reagentBatch().reagentName() + "=" + b.reagentBatch().lotNumber())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("—");
            sb.append(" Reagent Lots: [").append(lots).append("].");
        }

        // Curve fit summary
        String curveSummary = formatCurveParams(exp.curveParameters());
        if (!curveSummary.isBlank()) {
            sb.append("\n").append(curveSummary);
        }

        // Per-pair detail for each type in display order
        if (exp.measurementPairs() != null) {
            for (PairType type : new PairType[]{PairType.CALIBRATION, PairType.CONTROL, PairType.SAMPLE}) {
                List<MeasurementPairResponse> typePairs = exp.measurementPairs().stream()
                        .filter(p -> type.equals(p.pairType()))
                        .toList();
                sb.append(formatPairsDetail(type.name(), typePairs, maxCvAllowed, maxErrorAllowed));
            }
        }

        return sb.toString();
    }

    /**
     * Produces a tabular per-pair listing for one {@link PairType}.
     * Each row includes signals, computed metrics, outlier flag, and pair status.
     * Cells exceeding the protocol limits are marked with ⚠️. Null numeric fields are shown as {@code —}.
     *
     * <p>Note: interpolated concentration and outlier reason are not included because they
     * are not part of {@link MeasurementPairResponse} and would require additional service calls.</p>
     *
     * @param label           display label (e.g. "CALIBRATION")
     * @param pairs           measurement pair DTOs for this type
     * @param maxCvAllowed    protocol %CV upper limit
     * @param maxErrorAllowed protocol %Error (|%Recovery − 100|) upper limit
     * @return multi-line markdown table string, or empty string when {@code pairs} is empty
     */
    private String formatPairsDetail(String label, List<MeasurementPairResponse> pairs,
                                      double maxCvAllowed, double maxErrorAllowed) {
        if (pairs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "\n%s (%d pair%s):", label, pairs.size(), pairs.size() == 1 ? "" : "s"));
        sb.append(String.format(Locale.ROOT,
                "%n  | # | Outlier | ConcNom | S1 | S2 | Mean | %%CV (≤%.1f%%) | %%Rec (err≤%.1f%%) | Status |",
                maxCvAllowed, maxErrorAllowed));
        sb.append("\n  |---|---------|---------|----|----|------|--------------|------------------|--------|");

        int idx = 1;
        for (MeasurementPairResponse p : pairs) {
            String outlierStr = Boolean.TRUE.equals(p.isOutlier()) ? "YES ⚠️" : "No";

            String cvCell;
            if (p.cvPct() != null) {
                boolean cvOver = p.cvPct() > maxCvAllowed;
                cvCell = String.format(Locale.ROOT, "%.1f%%%s", p.cvPct(), cvOver ? " ⚠️" : "");
            } else {
                cvCell = "—";
            }

            String recCell;
            if (p.recoveryPct() != null) {
                boolean recOver = Math.abs(p.recoveryPct() - 100.0) > maxErrorAllowed;
                recCell = String.format(Locale.ROOT, "%.1f%%%s", p.recoveryPct(), recOver ? " ⚠️" : "");
            } else {
                recCell = "—";
            }

            String status = p.pairStatus() != null ? p.pairStatus().name() : "—";
            String conc   = p.concentrationNominal() != null ? String.format(Locale.ROOT, "%.4f", p.concentrationNominal()) : "—";
            String s1     = p.signal1()   != null ? String.format(Locale.ROOT, "%.4f", p.signal1())    : "—";
            String s2     = p.signal2()   != null ? String.format(Locale.ROOT, "%.4f", p.signal2())    : "—";
            String mean   = p.signalMean() != null ? String.format(Locale.ROOT, "%.4f", p.signalMean()) : "—";

            sb.append(String.format(Locale.ROOT,
                    "%n  | %d | %s | %s | %s | %s | %s | %s | %s | %s |",
                    idx++, outlierStr, conc, s1, s2, mean, cvCell, recCell, status));
        }
        return sb.toString();
    }

    /**
     * Extracts goodness-of-fit metrics and EC50 CI from the JSON {@code curveParameters} string
     * stored on the experiment. Returns an empty string when the field is null, blank, or
     * cannot be parsed — the caller simply omits the curve section from the prompt.
     *
     * @param curveParameters JSON string from {@link it.elismart_lims.dto.ExperimentResponse#curveParameters()}
     * @return a single-line summary such as {@code "Curve fit: R²=0.9987 | RMSE=0.0021 | EC50=12.340 ..."},
     *         or {@code ""} on failure
     */
    private String formatCurveParams(String curveParameters) {
        if (curveParameters == null || curveParameters.isBlank()) {
            return "";
        }
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> params =
                    objectMapper.readValue(curveParameters, java.util.Map.class);

            Object r2          = params.get("_r2");
            Object rmse        = params.get("_rmse");
            Object convergence = params.get("_convergence");
            Object ec50        = params.get("C");
            Object ec50Lower   = params.get("_ec50_lower95");
            Object ec50Upper   = params.get("_ec50_upper95");
            Object flatWarn    = params.get("_flat_segment_warning");

            StringBuilder content = new StringBuilder();

            if (r2 != null) content.append(String.format(Locale.ROOT, "R²=%.4f", ((Number) r2).doubleValue()));
            if (rmse != null) {
                if (content.length() > 0) content.append(" | ");
                content.append(String.format(Locale.ROOT, "RMSE=%.4f", ((Number) rmse).doubleValue()));
            }
            if (ec50 != null) {
                if (content.length() > 0) content.append(" | ");
                content.append(String.format(Locale.ROOT, "EC50=%.3f", ((Number) ec50).doubleValue()));
                if (ec50Lower != null && ec50Upper != null) {
                    content.append(String.format(Locale.ROOT, " (95%% CI: %.3f\u2013%.3f)",
                            ((Number) ec50Lower).doubleValue(), ((Number) ec50Upper).doubleValue()));
                }
            }
            if (convergence != null) {
                if (content.length() > 0) content.append(" | ");
                content.append(((Number) convergence).doubleValue() == 1.0 ? "Converged" : "NOT converged");
            }
            if (flatWarn != null) {
                if (content.length() > 0) content.append(" | ");
                content.append("\u26a0\ufe0f Flat calibration segment");
            }

            return content.isEmpty() ? "" : "Curve fit: " + content;
        } catch (Exception e) {
            log.warn("Failed to parse curveParameters JSON — omitting curve section from AI prompt: {}",
                    e.getMessage());
            return "";
        }
    }
}

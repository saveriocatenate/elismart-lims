package it.elismart_lims.controller;

import it.elismart_lims.dto.AiInsightResponse;
import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.service.AiInsightService;
import it.elismart_lims.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * REST controller exposing the Gemini AI analysis endpoint.
 *
 * <p>Delegates all business logic to {@link GeminiService}.</p>
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;
    private final AiInsightService aiInsightService;

    /**
     * Analyze a set of experiments using the Gemini AI model.
     *
     * <p>The request must include at least two experiment IDs and a non-empty user question.</p>
     *
     * @param request the analysis request payload
     * @return 200 OK with the AI-generated analysis
     */
    @PostMapping("/analyze")
    public ResponseEntity<GeminiAnalysisResponse> analyze(
            @Valid @RequestBody GeminiAnalysisRequest request) {
        GeminiAnalysisResponse response = geminiService.analyze(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns all persisted AI insights that include the specified experiment,
     * ordered by {@code generatedAt} descending (most recent first).
     *
     * @param experimentId the experiment primary key
     * @return 200 OK with the list of insights (may be empty)
     */
    @GetMapping("/insights")
    public ResponseEntity<List<AiInsightResponse>> getInsights(
            @RequestParam Long experimentId) {
        List<AiInsightResponse> insights = aiInsightService.getByExperimentId(experimentId);
        return ResponseEntity.ok(insights);
    }
}

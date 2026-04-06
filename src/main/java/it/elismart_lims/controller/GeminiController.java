package it.elismart_lims.controller;

import it.elismart_lims.dto.GeminiAnalysisRequest;
import it.elismart_lims.dto.GeminiAnalysisResponse;
import it.elismart_lims.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestBody GeminiAnalysisRequest request) {
        GeminiAnalysisResponse response = geminiService.analyze(request);
        return ResponseEntity.ok(response);
    }
}

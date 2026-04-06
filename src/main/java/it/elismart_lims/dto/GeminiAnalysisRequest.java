package it.elismart_lims.dto;

import java.util.List;

/**
 * Request payload for the Gemini AI analysis endpoint.
 *
 * @param experimentIds the IDs of the experiments to compare and analyze
 * @param userQuestion  the analyst's free-text question to the AI
 */
public record GeminiAnalysisRequest(List<Long> experimentIds, String userQuestion) {
}

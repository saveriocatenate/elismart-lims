package it.elismart_lims.dto;

import lombok.Builder;

/**
 * Response payload carrying the AI-generated analysis text.
 *
 * @param analysis the full analysis text produced by the Gemini model
 */
@Builder
public record GeminiAnalysisResponse(String analysis) {
}

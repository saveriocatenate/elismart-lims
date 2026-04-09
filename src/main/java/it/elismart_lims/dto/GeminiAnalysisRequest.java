package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload for the Gemini AI analysis endpoint.
 *
 * @param experimentIds the IDs of the experiments to compare and analyze (at least one required)
 * @param userQuestion  the analyst's free-text question to the AI (must not be blank)
 */
public record GeminiAnalysisRequest(
        @NotEmpty(message = "At least one experiment ID is required")
        List<Long> experimentIds,
        @NotBlank(message = "User question must not be blank")
        String userQuestion
) {
}

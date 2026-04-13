package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a persisted AI analysis insight.
 *
 * @param id            the insight primary key
 * @param userQuestion  the analyst's original question
 * @param aiResponse    the full AI-generated analysis text
 * @param generatedAt   timestamp when the analysis was produced
 * @param generatedBy   username of the principal who triggered the analysis
 * @param experimentIds IDs of the experiments included in this analysis
 */
@Builder
public record AiInsightResponse(
        Long id,
        String userQuestion,
        String aiResponse,
        LocalDateTime generatedAt,
        String generatedBy,
        List<Long> experimentIds
) {
}

package it.elismart_lims.mapper;

import it.elismart_lims.dto.AiInsightResponse;
import it.elismart_lims.model.AiInsight;
import it.elismart_lims.model.Experiment;

import java.util.List;

/**
 * Static mapper between {@link AiInsight} entities and their DTOs.
 *
 * <p>This class is a static utility: it is {@code final}, all methods are {@code static},
 * and the private constructor prevents instantiation. Never inject this class as a bean.</p>
 */
public final class AiInsightMapper {

    private AiInsightMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a single {@link AiInsight} entity to an {@link AiInsightResponse} DTO.
     *
     * <p>The experiment IDs are extracted from the entity's {@code experiments} collection,
     * which must already be initialised (the caller is responsible for eager loading or
     * operating inside a transaction).</p>
     *
     * @param insight the AI insight entity
     * @return the corresponding response DTO
     */
    public static AiInsightResponse toResponse(AiInsight insight) {
        List<Long> experimentIds = insight.getExperiments() == null
                ? List.of()
                : insight.getExperiments().stream()
                        .map(Experiment::getId)
                        .toList();

        return AiInsightResponse.builder()
                .id(insight.getId())
                .userQuestion(insight.getUserQuestion())
                .aiResponse(insight.getAiResponse())
                .generatedAt(insight.getGeneratedAt())
                .generatedBy(insight.getGeneratedBy())
                .experimentIds(experimentIds)
                .build();
    }

    /**
     * Converts a list of {@link AiInsight} entities to a list of {@link AiInsightResponse} DTOs.
     * Order is preserved.
     *
     * @param insights the list of insight entities
     * @return the corresponding list of response DTOs
     */
    public static List<AiInsightResponse> toResponseList(List<AiInsight> insights) {
        return insights.stream().map(AiInsightMapper::toResponse).toList();
    }
}

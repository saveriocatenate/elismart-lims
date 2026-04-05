package it.elismart_lims.dto;

import java.util.List;

/**
 * Paginated response container for experiment search results.
 */
public record ExperimentPage(
        List<ExperimentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
package it.elismart_lims.dto;

import java.time.LocalDateTime;

/**
 * Search criteria for filtering experiments.
 */
public record ExperimentSearchRequest(
        String name,
        LocalDateTime date,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        String status,
        int page,
        int size
) {
}
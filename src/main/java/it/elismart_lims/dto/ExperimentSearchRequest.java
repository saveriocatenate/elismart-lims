package it.elismart_lims.dto;

import it.elismart_lims.model.ExperimentStatus;

import java.time.LocalDateTime;

/**
 * Search criteria for filtering experiments.
 * {@code status} is typed as {@link ExperimentStatus} so invalid values are rejected
 * at deserialization time rather than silently producing empty result sets.
 */
public record ExperimentSearchRequest(
        String name,
        LocalDateTime date,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        ExperimentStatus status,
        int page,
        int size
) {
}
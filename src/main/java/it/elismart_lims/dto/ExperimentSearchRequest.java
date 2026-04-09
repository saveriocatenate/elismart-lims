package it.elismart_lims.dto;

import it.elismart_lims.model.ExperimentStatus;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * Search criteria for filtering experiments.
 *
 * <p>{@code status} is typed as {@link ExperimentStatus} so invalid values are rejected
 * at deserialization time rather than silently producing empty result sets.</p>
 *
 * <p>{@code page} must be ≥ 0 and {@code size} must be ≥ 1 to prevent negative-offset
 * or zero-limit pageable queries.</p>
 */
public record ExperimentSearchRequest(
        String name,
        LocalDateTime date,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        ExperimentStatus status,
        @Min(value = 0, message = "page must be >= 0")
        int page,
        @Min(value = 1, message = "size must be >= 1")
        int size
) {
}
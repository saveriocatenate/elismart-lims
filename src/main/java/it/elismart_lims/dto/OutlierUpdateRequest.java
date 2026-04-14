package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for the PATCH /api/measurement-pairs/{id}/outlier endpoint.
 *
 * <p>When an outlier flag is manually overridden, an electronic justification
 * is required in the {@code reason} field per ALCOA+ traceability principles
 * and 21 CFR Part 11 §11.10(e). The {@code reason} is persisted in {@code audit_log}.</p>
 */
public record OutlierUpdateRequest(
        /** New outlier flag value; must not be null. */
        @NotNull(message = "isOutlier must not be null")
        Boolean isOutlier,
        /**
         * Free-text justification for this manual outlier override.
         * Persisted in {@code audit_log} when the flag value changes.
         */
        String reason
) {
}

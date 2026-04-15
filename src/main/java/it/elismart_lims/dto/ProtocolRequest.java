package it.elismart_lims.dto;

import it.elismart_lims.model.CurveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for creating or updating a Protocol.
 *
 * <p>All numeric limit and count fields are validated as strictly positive at the HTTP
 * boundary so that invalid values are rejected with HTTP 400 before reaching the service
 * layer.</p>
 */
public record ProtocolRequest(
        @NotBlank String name,
        @NotNull @Positive(message = "numCalibrationPairs must be positive") Integer numCalibrationPairs,
        @NotNull @Positive(message = "numControlPairs must be positive") Integer numControlPairs,
        @NotNull @Positive(message = "maxCvAllowed must be positive") Double maxCvAllowed,
        @NotNull @Positive(message = "maxErrorAllowed must be positive") Double maxErrorAllowed,
        @NotNull CurveType curveType
) {
}

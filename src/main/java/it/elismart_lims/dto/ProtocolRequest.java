package it.elismart_lims.dto;

import it.elismart_lims.model.CurveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating or updating a Protocol.
 */
public record ProtocolRequest(
        @NotBlank String name,
        @NotNull Integer numCalibrationPairs,
        @NotNull Integer numControlPairs,
        @NotNull Double maxCvAllowed,
        @NotNull Double maxErrorAllowed,
        @NotNull CurveType curveType
) {
}

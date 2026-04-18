package it.elismart_lims.dto;

import it.elismart_lims.model.CurveType;
import lombok.Builder;

/**
 * Response payload for Protocol entities.
 */
@Builder
public record ProtocolResponse(
        Long id,
        String name,
        Integer numCalibrationPairs,
        Integer numControlPairs,
        Double maxCvAllowed,
        Double maxErrorAllowed,
        CurveType curveType,
        String concentrationUnit
) {
}

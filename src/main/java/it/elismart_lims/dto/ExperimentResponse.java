package it.elismart_lims.dto;

import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.ExperimentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for Experiment entities with nested collections and audit metadata.
 */
@Builder
public record ExperimentResponse(
        Long id,
        String name,
        LocalDateTime date,
        ExperimentStatus status,
        String protocolName,
        CurveType protocolCurveType,
        /* Maximum %CV allowed by the protocol; {@code null} if not configured. */
        Double protocolMaxCvAllowed,
        /* Maximum %Error (recovery deviation from 100%) allowed by the protocol; {@code null} if not configured. */
        Double protocolMaxErrorAllowed,
        /* Unit of measurement for concentrations in the linked protocol (e.g. "ng/mL"). */
        String protocolConcentrationUnit,
        /* JSON-serialized calibration curve parameters; {@code null} before first validation. */
        String curveParameters,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy,
        List<UsedReagentBatchResponse> usedReagentBatches,
        List<MeasurementPairResponse> measurementPairs
) {
}

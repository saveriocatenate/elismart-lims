package it.elismart_lims.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Response payload for {@link it.elismart_lims.model.Sample} entities.
 */
@Builder
public record SampleResponse(
        Long id,
        String barcode,
        String matrixType,
        String patientId,
        String studyId,
        LocalDate collectionDate,
        String preparationMethod,
        String notes
) {
}

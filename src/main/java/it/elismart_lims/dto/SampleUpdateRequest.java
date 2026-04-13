package it.elismart_lims.dto;

import java.time.LocalDate;

/**
 * Request payload for updating an existing {@link it.elismart_lims.model.Sample}.
 *
 * <p>All fields are optional. A {@code null} value means "leave unchanged".</p>
 */
public record SampleUpdateRequest(
        String matrixType,
        String patientId,
        String studyId,
        LocalDate collectionDate,
        String preparationMethod,
        String notes
) {
}

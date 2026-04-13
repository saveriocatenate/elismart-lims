package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request payload for creating a new {@link it.elismart_lims.model.Sample}.
 *
 * <p>Only {@code barcode} is required; all other fields are optional.</p>
 */
public record SampleCreateRequest(
        @NotBlank String barcode,
        String matrixType,
        String patientId,
        String studyId,
        LocalDate collectionDate,
        String preparationMethod,
        String notes
) {
}

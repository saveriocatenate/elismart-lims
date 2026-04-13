package it.elismart_lims.mapper;

import it.elismart_lims.dto.SampleCreateRequest;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.model.Sample;

/**
 * Static mapper between {@link Sample} entities and their DTOs.
 */
public final class SampleMapper {

    private SampleMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a {@link SampleCreateRequest} DTO into a {@link Sample} entity.
     *
     * @param request the creation payload
     * @return the built Sample entity (not yet persisted)
     */
    public static Sample toEntity(SampleCreateRequest request) {
        return Sample.builder()
                .barcode(request.barcode())
                .matrixType(request.matrixType())
                .patientId(request.patientId())
                .studyId(request.studyId())
                .collectionDate(request.collectionDate())
                .preparationMethod(request.preparationMethod())
                .notes(request.notes())
                .build();
    }

    /**
     * Converts a {@link Sample} entity into a {@link SampleResponse} DTO.
     *
     * @param entity the Sample entity
     * @return the response DTO
     */
    public static SampleResponse toResponse(Sample entity) {
        return SampleResponse.builder()
                .id(entity.getId())
                .barcode(entity.getBarcode())
                .matrixType(entity.getMatrixType())
                .patientId(entity.getPatientId())
                .studyId(entity.getStudyId())
                .collectionDate(entity.getCollectionDate())
                .preparationMethod(entity.getPreparationMethod())
                .notes(entity.getNotes())
                .build();
    }
}

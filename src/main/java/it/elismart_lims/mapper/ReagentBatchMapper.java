package it.elismart_lims.mapper;

import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.dto.ReagentBatchResponse;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.model.ReagentCatalog;

import java.util.List;

/**
 * Static mapper between {@link ReagentBatch} entities and their DTOs.
 */
public final class ReagentBatchMapper {

    private ReagentBatchMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a {@link ReagentBatch} entity from a create request and the resolved reagent entity.
     *
     * @param request the validated create request
     * @param reagent the parent {@link ReagentCatalog} entity
     * @return the built (unsaved) entity
     */
    public static ReagentBatch toEntity(ReagentBatchCreateRequest request, ReagentCatalog reagent) {
        return ReagentBatch.builder()
                .reagent(reagent)
                .lotNumber(request.lotNumber())
                .expiryDate(request.expiryDate())
                .supplier(request.supplier())
                .notes(request.notes())
                .build();
    }

    /**
     * Converts a {@link ReagentBatch} entity to a {@link ReagentBatchResponse} DTO.
     *
     * <p>The parent {@link ReagentCatalog} must be accessible (not lazily unloaded) at call time.</p>
     *
     * @param entity the entity to convert
     * @return the response DTO including reagent name and manufacturer
     */
    public static ReagentBatchResponse toResponse(ReagentBatch entity) {
        return ReagentBatchResponse.builder()
                .id(entity.getId())
                .reagentId(entity.getReagent().getId())
                .reagentName(entity.getReagent().getName())
                .manufacturer(entity.getReagent().getManufacturer())
                .lotNumber(entity.getLotNumber())
                .expiryDate(entity.getExpiryDate())
                .supplier(entity.getSupplier())
                .notes(entity.getNotes())
                .build();
    }

    /**
     * Converts a list of {@link ReagentBatch} entities to response DTOs.
     *
     * @param entities the list to convert
     * @return the list of response DTOs
     */
    public static List<ReagentBatchResponse> toResponseList(List<ReagentBatch> entities) {
        return entities.stream().map(ReagentBatchMapper::toResponse).toList();
    }
}

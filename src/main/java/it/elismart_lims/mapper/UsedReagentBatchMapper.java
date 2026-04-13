package it.elismart_lims.mapper;

import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.model.UsedReagentBatch;

import java.util.List;

/**
 * Static mapper between {@link UsedReagentBatch} entities and their DTOs.
 */
public final class UsedReagentBatchMapper {

    private UsedReagentBatchMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a {@link UsedReagentBatch} entity into a {@link UsedReagentBatchResponse} DTO.
     *
     * <p>The nested {@link it.elismart_lims.dto.ReagentBatchResponse} is built via
     * {@link ReagentBatchMapper#toResponse}, which requires the reagent association on the
     * {@link it.elismart_lims.model.ReagentBatch} to be loaded.</p>
     *
     * @param entity the UsedReagentBatch entity
     * @return the response DTO
     */
    public static UsedReagentBatchResponse toResponse(UsedReagentBatch entity) {
        return UsedReagentBatchResponse.builder()
                .id(entity.getId())
                .reagentBatch(ReagentBatchMapper.toResponse(entity.getReagentBatch()))
                .build();
    }

    /**
     * Converts a list of {@link UsedReagentBatch} entities into response DTOs.
     *
     * @param entities the list of entities
     * @return the list of response DTOs
     */
    public static List<UsedReagentBatchResponse> toResponseList(List<UsedReagentBatch> entities) {
        return entities.stream().map(UsedReagentBatchMapper::toResponse).toList();
    }
}

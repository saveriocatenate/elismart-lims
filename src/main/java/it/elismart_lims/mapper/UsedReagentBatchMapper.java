package it.elismart_lims.mapper;

import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.model.UsedReagentBatch;

import java.util.List;

/**
 * Static mapper between UsedReagentBatch entities and their DTOs.
 */
public final class UsedReagentBatchMapper {

    private UsedReagentBatchMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a UsedReagentBatch entity into a UsedReagentBatchResponse DTO.
     *
     * @param entity the UsedReagentBatch entity
     * @return the response DTO
     */
    public static UsedReagentBatchResponse toResponse(UsedReagentBatch entity) {
        return UsedReagentBatchResponse.builder()
                .id(entity.getId())
                .reagentName(entity.getReagent().getName())
                .lotNumber(entity.getLotNumber())
                .expiryDate(entity.getExpiryDate())
                .build();
    }

    /**
     * Converts a list of UsedReagentBatch entities into response DTOs.
     *
     * @param entities the list of UsedReagentBatch entities
     * @return the list of response DTOs
     */
    public static List<UsedReagentBatchResponse> toResponseList(List<UsedReagentBatch> entities) {
        return entities.stream().map(UsedReagentBatchMapper::toResponse).toList();
    }
}

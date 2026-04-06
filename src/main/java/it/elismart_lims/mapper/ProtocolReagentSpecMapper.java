package it.elismart_lims.mapper;

import it.elismart_lims.dto.ProtocolReagentSpecResponse;
import it.elismart_lims.model.ProtocolReagentSpec;

/**
 * Static mapper between ProtocolReagentSpec entities and their DTOs.
 */
public final class ProtocolReagentSpecMapper {

    private ProtocolReagentSpecMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a ProtocolReagentSpec entity into a ProtocolReagentSpecResponse DTO.
     *
     * @param entity the ProtocolReagentSpec entity
     * @return the response DTO
     */
    public static ProtocolReagentSpecResponse toResponse(ProtocolReagentSpec entity) {
        return ProtocolReagentSpecResponse.builder()
                .withId(entity.getId())
                .withProtocolId(entity.getProtocol().getId())
                .withReagentId(entity.getReagent().getId())
                .withReagentName(entity.getReagent().getName())
                .withIsMandatory(entity.getIsMandatory())
                .build();
    }
}

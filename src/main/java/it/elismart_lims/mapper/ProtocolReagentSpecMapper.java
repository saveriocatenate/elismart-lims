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
                .id(entity.getId())
                .protocolId(entity.getProtocol().getId())
                .reagentId(entity.getReagent().getId())
                .reagentName(entity.getReagent().getName())
                .isMandatory(entity.getIsMandatory())
                .build();
    }
}

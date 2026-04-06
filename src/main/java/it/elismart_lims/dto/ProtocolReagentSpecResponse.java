package it.elismart_lims.dto;

import lombok.Builder;

/**
 * Response payload for ProtocolReagentSpec entities.
 */
@Builder
public record ProtocolReagentSpecResponse(
        Long id,
        Long protocolId,
        Long reagentId,
        String reagentName,
        Boolean isMandatory
) {
}

package it.elismart_lims.dto;

/**
 * Response payload for ProtocolReagentSpec entities.
 */
public record ProtocolReagentSpecResponse(
        Long id,
        Long protocolId,
        Long reagentId,
        Boolean isMandatory
) {
}

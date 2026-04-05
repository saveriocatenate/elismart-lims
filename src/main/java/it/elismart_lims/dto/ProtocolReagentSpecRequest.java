package it.elismart_lims.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating a ProtocolReagentSpec.
 */
public record ProtocolReagentSpecRequest(
        @NotNull Long protocolId,
        @NotNull Long reagentId,
        @NotNull Boolean isMandatory
) {
}

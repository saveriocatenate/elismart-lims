package it.elismart_lims.dto;

/**
 * Response payload for ProtocolReagentSpec entities.
 */
public record ProtocolReagentSpecResponse(
        Long id,
        Long protocolId,
        Long reagentId,
        String reagentName,
        Boolean isMandatory
) {
    public static final class Builder {
        private Long id;
        private Long protocolId;
        private Long reagentId;
        private String reagentName;
        private Boolean isMandatory;
        public Builder withId(Long id) { this.id = id; return this; }
        public Builder withProtocolId(Long protocolId) { this.protocolId = protocolId; return this; }
        public Builder withReagentId(Long reagentId) { this.reagentId = reagentId; return this; }
        public Builder withReagentName(String reagentName) { this.reagentName = reagentName; return this; }
        public Builder withIsMandatory(Boolean isMandatory) { this.isMandatory = isMandatory; return this; }
        public ProtocolReagentSpecResponse build() {
            return new ProtocolReagentSpecResponse(id, protocolId, reagentId, reagentName, isMandatory);
        }
    }
    public static Builder builder() {
        return new Builder();
    }
}

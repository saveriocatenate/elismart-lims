package it.elismart_lims.dto;

import java.time.LocalDate;

/**
 * Response payload for UsedReagentBatch entities.
 */
public record UsedReagentBatchResponse(
        Long id,
        String reagentName,
        String lotNumber,
        LocalDate expiryDate
) {
    public static final class Builder  {
        private Long id;
        private String reagentName;
        private String lotNumber;
        private LocalDate expiryDate;

        public Builder withId(Long id) {
            this.id = id;
            return this;
        }
        public Builder withReagentName(String reagentName) {
            this.reagentName = reagentName;
            return this;
        }
        public Builder withLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
            return this;
        }
        public Builder withExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public UsedReagentBatchResponse build() {
            return new UsedReagentBatchResponse(id, reagentName, lotNumber, expiryDate);
        }
    }
    public static Builder builder() {
        return new Builder();
    }
}

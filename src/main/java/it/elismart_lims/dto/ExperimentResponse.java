package it.elismart_lims.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for Experiment entities with nested collections.
 */
public record ExperimentResponse(
        Long id,
        String name,
        LocalDateTime date,
        String status,
        String protocolName,
        List<UsedReagentBatchResponse> usedReagentBatches,
        List<MeasurementPairResponse> measurementPairs
) {
    public static final class Builder {
        private Long id;
        private String name;
        private LocalDateTime date;
        private String status;
        private String protocolName;
        private List<UsedReagentBatchResponse> usedReagentBatches;
        private List<MeasurementPairResponse> measurementPairs;

        public Builder withId(Long id) {
            this.id = id;
            return this;
        }
        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withDate(LocalDateTime date) {
            this.date = date;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder withProtocolName(String protocolName) {
            this.protocolName = protocolName;
            return this;
        }

        public Builder withUsedReagentBatches(List<UsedReagentBatchResponse> usedReagentBatches) {
            this.usedReagentBatches = usedReagentBatches;
            return this;
        }
        public Builder withMeasurementPairs(List<MeasurementPairResponse> measurementPairs) {
            this.measurementPairs = measurementPairs;
            return this;
        }

        public ExperimentResponse build() {
            return new ExperimentResponse(id, name, date, status, protocolName, usedReagentBatches, measurementPairs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

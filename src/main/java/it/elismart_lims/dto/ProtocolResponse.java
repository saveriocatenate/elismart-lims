package it.elismart_lims.dto;

/**
 * Response payload for Protocol entities.
 */
public record ProtocolResponse(
        Long id,
        String name,
        Integer numCalibrationPairs,
        Integer numControlPairs,
        Double maxCvAllowed,
        Double maxErrorAllowed
) {

    public static final class Builder {
        private Long id;
        private String name;
        private Integer numCalibrationPairs;
        private Integer numControlPairs;
        private Double maxCvAllowed;
        private Double maxErrorAllowed;
        public Builder withId(Long id) {
            this.id = id;
            return this;
        }
        public Builder withName(String name) {
            this.name = name;

            return this;

        }
        public Builder withNumCalibrationPairs(Integer numCalibrationPairs) {
            this.numCalibrationPairs = numCalibrationPairs;
            return this;
        }
        public Builder withNumControlPairs(Integer numControlPairs) {
            this.numControlPairs = numControlPairs;
            return this;
        }
        public Builder withMaxCvAllowed(Double maxCvAllowed) {
            this.maxCvAllowed = maxCvAllowed;
            return this;
        }
        public Builder withMaxErrorAllowed(Double maxErrorAllowed) {
            this.maxErrorAllowed = maxErrorAllowed;
            return this;
        }
        public ProtocolResponse build() {
            return new ProtocolResponse(id, name, numCalibrationPairs, numControlPairs, maxCvAllowed, maxErrorAllowed);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

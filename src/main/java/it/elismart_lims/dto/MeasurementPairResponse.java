package it.elismart_lims.dto;

/**
 * Response payload for MeasurementPair entities.
 */
public record MeasurementPairResponse(
        Long id,
        String pairType,
        Double concentrationNominal,
        Double signal1,
        Double signal2,
        Double signalMean,
        Double cvPct,
        Double recoveryPct,
        Boolean isOutlier
) {

    public static final class Builder {
        private Long id;
        private String pairType;
        private Double concentrationNominal;
        private Double signal1;
        private Double signal2;
        private Double signalMean;
        private Double cvPct;
        private Double recoveryPct;
        private Boolean isOutlier;

        public Builder withId(Long id) {
            this.id = id;
            return this;
        }
        public Builder withPairType(String pairType) {
            this.pairType = pairType;
            return this;
        }
        public Builder withConcentrationNominal(Double concentrationNominal) {
            this.concentrationNominal = concentrationNominal;
            return this;
        }
        public Builder withSignal1(Double signal1) {
            this.signal1 = signal1;
            return this;
        }
        public Builder withSignal2(Double signal2) {
            this.signal2 = signal2;
            return this;
        }
        public Builder withSignalMean(Double signalMean) {
            this.signalMean = signalMean;
            return this;
        }
        public Builder withCvPct(Double cvPct) {
            this.cvPct = cvPct;
            return this;
        }
        public Builder withRecoveryPct(Double recoveryPct) {
            this.recoveryPct = recoveryPct;
            return this;
        }
        public Builder withIsOutlier(Boolean isOutlier) {
            this.isOutlier = isOutlier;
            return this;
        }
        public MeasurementPairResponse build() {
            return new MeasurementPairResponse(id, pairType,concentrationNominal, signal1, signal2, signalMean, cvPct, recoveryPct, isOutlier);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single replicate measurement within an experiment, storing raw signals,
 * calculated mean, precision (%CV), and accuracy (%Recovery).
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "measurement_pair")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementPair extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    /**
     * Classification of this replicate (calibration point, quality control, or unknown sample).
     * Stored as its string name; constrained by a DB CHECK to the {@link PairType} values.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pair_type", nullable = false, length = 50)
    private PairType pairType;

    /** Expected (nominal) concentration; used for calibrators. Null for controls and unknowns. */
    @Column(name = "concentration_nominal")
    private Double concentrationNominal;

    /** Raw optical density reading from the first replicate measurement. */
    @Column(name = "signal_1")
    private Double signal1;

    /** Raw optical density reading from the second replicate measurement. */
    @Column(name = "signal_2")
    private Double signal2;

    /** Arithmetic mean of {@link #signal1} and {@link #signal2}. */
    @Column(name = "signal_mean")
    private Double signalMean;

    /** Precision expressed as coefficient of variation (StdDev / Mean × 100). */
    @Column(name = "cv_pct")
    private Double cvPct;

    /** Accuracy expressed as percent recovery (measured / nominal × 100). */
    @Column(name = "recovery_pct")
    private Double recoveryPct;

    /** {@code true} if this pair has been flagged as an outlier (manually or automatically). */
    @Column(name = "is_outlier", nullable = false)
    private Boolean isOutlier;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeasurementPair that = (MeasurementPair) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
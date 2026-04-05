package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single replicate measurement within an experiment, storing raw signals,
 * calculated mean, precision (%CV), and accuracy (%Recovery).
 */
@Entity
@Table(name = "measurement_pair")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Column(name = "pair_type", nullable = false)
    private String pairType;

    @Column(name = "concentration_nominal")
    private Double concentrationNominal;

    @Column(name = "signal_1")
    private Double signal1;

    @Column(name = "signal_2")
    private Double signal2;

    @Column(name = "signal_mean")
    private Double signalMean;

    @Column(name = "cv_pct")
    private Double cvPct;

    @Column(name = "recovery_pct")
    private Double recoveryPct;

    @Column(name = "is_outlier", nullable = false)
    private Boolean isOutlier;
}
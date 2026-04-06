package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Defines the test methodology for an assay, including curve type expectations,
 * acceptable recovery/CV ranges, and the number of calibration/control pairs required.
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "protocol")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Protocol extends Auditable {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique name identifying the protocol (e.g. "ELISA IgG Test"). */
    @Column(nullable = false, unique = true)
    private String name;

    /** Expected number of calibration replicate pairs per experiment run. */
    @Column(name = "num_calibration_pairs", nullable = false)
    private Integer numCalibrationPairs;

    /** Expected number of quality-control replicate pairs per experiment run. */
    @Column(name = "num_control_pairs", nullable = false)
    private Integer numControlPairs;

    /** Maximum acceptable %CV between replicates (precision limit). */
    @Column(name = "max_cv_allowed", nullable = false)
    private Double maxCvAllowed;

    /** Maximum acceptable %Recovery error (accuracy limit). */
    @Column(name = "max_error_allowed", nullable = false)
    private Double maxErrorAllowed;
}
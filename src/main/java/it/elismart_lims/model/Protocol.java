package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Defines the test methodology for an assay, including curve type expectations,
 * acceptable recovery/CV ranges, and the number of calibration/control pairs required.
 */
@Entity
@Table(name = "protocol")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Protocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "num_calibration_pairs", nullable = false)
    private Integer numCalibrationPairs;

    @Column(name = "num_control_pairs", nullable = false)
    private Integer numControlPairs;

    @Column(name = "max_cv_allowed", nullable = false)
    private Double maxCvAllowed;

    @Column(name = "max_error_allowed", nullable = false)
    private Double maxErrorAllowed;
}
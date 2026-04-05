package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Tracks the specific reagent lot number used in an experiment for full traceability.
 */
@Entity
@Table(name = "used_reagent_batch",
       uniqueConstraints = @UniqueConstraint(columnNames = {"experiment_id", "reagent_id", "lot_number"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsedReagentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_id", nullable = false)
    private ReagentCatalog reagent;

    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}
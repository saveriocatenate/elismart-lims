package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An actual instance of a protocol run on the lab bench, with metadata
 * about the date and overall validation status.
 *
 * <p>Audit columns ({@code created_at}, {@code updated_at}, {@code created_by})
 * are inherited from {@link Auditable} and populated automatically by Spring
 * Data JPA auditing.</p>
 */
@Entity
@Table(name = "experiment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment extends Auditable {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable label for this experiment run (e.g. "IgG Run 2026-04-06"). */
    @Column(nullable = false)
    private String name;

    /** Date and time the experiment was performed on the lab bench. */
    @Column(nullable = false)
    private LocalDateTime date;

    /**
     * Lifecycle and validation outcome of this experiment run.
     * Stored as its string name; constrained by a DB CHECK to the {@link ExperimentStatus} values.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExperimentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private Protocol protocol;

    /**
     * Reagent lot numbers recorded for this experiment run.
     * {@code orphanRemoval = true} ensures that removing a batch from this collection
     * deletes the corresponding row from the database — preventing orphaned records.
     * {@code @BatchSize} avoids the N+1 query problem when loading a page of experiments:
     * Hibernate fetches all batches for the page in a single IN-clause query.
     */
    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 50)
    private List<UsedReagentBatch> usedReagentBatches = new ArrayList<>();

    /**
     * Measurement replicates recorded during this experiment run.
     * {@code orphanRemoval = true} ensures that removing a pair from this collection
     * deletes the corresponding row from the database — preventing orphaned records.
     * {@code @BatchSize} avoids the N+1 query problem (same rationale as {@link #usedReagentBatches}).
     */
    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @BatchSize(size = 50)
    private List<MeasurementPair> measurementPairs = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Experiment that = (Experiment) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
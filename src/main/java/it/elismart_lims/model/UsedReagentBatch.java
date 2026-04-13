package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Links a specific {@link ReagentBatch} to an {@link Experiment} for full lot traceability.
 *
 * <p>The combination of experiment and reagent batch is unique: each physical lot can appear
 * at most once per experiment.</p>
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "used_reagent_batch",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_urb_exp_reagent_batch",
           columnNames = {"experiment_id", "reagent_batch_id"}
       ))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsedReagentBatch extends Auditable {

    /** Primary key, auto-generated. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The experiment this entry belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    /**
     * Denormalised link to the reagent catalog entry for convenient querying.
     * Kept in sync with {@link ReagentBatch#getReagent()} at write time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_id", nullable = false)
    private ReagentCatalog reagent;

    /** The physical reagent lot used in this experiment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_batch_id", nullable = false)
    private ReagentBatch reagentBatch;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsedReagentBatch that = (UsedReagentBatch) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
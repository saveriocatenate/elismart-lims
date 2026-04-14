package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Physical lot/batch of a reagent, registered once and referenced by multiple experiments.
 *
 * <p>The combination of {@code reagent} and {@code lotNumber} is unique at the database
 * level via {@link UniqueConstraint}.</p>
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(
    name = "reagent_batch",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_reagent_lot",
        columnNames = {"reagent_id", "lot_number"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReagentBatch extends Auditable {

    /** Primary key, auto-generated. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The reagent this batch belongs to.
     * A batch cannot exist without its parent reagent catalog entry.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_id", nullable = false)
    private ReagentCatalog reagent;

    /** Manufacturer's lot number that uniquely identifies this batch. */
    @Column(name = "lot_number", nullable = false, length = 100)
    private String lotNumber;

    /** Expiry date printed on the label for this batch. */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /**
     * Optional supplier or distributor for this specific batch.
     * May differ from the reagent manufacturer (e.g. a local distributor).
     */
    @Column()
    private String supplier;

    /** Optional free-text notes (storage conditions, QC results, etc.). */
    @Column(length = 2000)
    private String notes;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentBatch that = (ReagentBatch) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bridge entity linking a protocol to a reagent, specifying whether
 * that reagent is mandatory for experiments using the protocol.
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "protocol_reagent_spec",
       uniqueConstraints = @UniqueConstraint(columnNames = {"protocol_id", "reagent_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolReagentSpec extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private Protocol protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_id", nullable = false)
    private ReagentCatalog reagent;

    /** Whether this reagent is required for all experiments using the parent protocol. */
    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProtocolReagentSpec that = (ProtocolReagentSpec) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bridge entity linking a protocol to a reagent, specifying whether
 * that reagent is mandatory for experiments using the protocol.
 */
@Entity
@Table(name = "protocol_reagent_spec",
       uniqueConstraints = @UniqueConstraint(columnNames = {"protocol_id", "reagent_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolReagentSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private Protocol protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reagent_id", nullable = false)
    private ReagentCatalog reagent;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory;
}
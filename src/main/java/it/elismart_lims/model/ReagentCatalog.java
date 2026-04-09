package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Master data for all laboratory reagents, including name, manufacturer, and optional description.
 *
 * <p>Audit columns are inherited from {@link Auditable}.</p>
 */
@Entity
@Table(name = "reagent_catalog")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReagentCatalog extends Auditable {

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Commercial or internal name of the reagent. */
    @Column(nullable = false)
    private String name;

    /** Name of the reagent manufacturer or supplier. */
    @Column(nullable = false)
    private String manufacturer;

    /** Optional free-text notes about the reagent (catalog reference, storage conditions, etc.). */
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentCatalog that = (ReagentCatalog) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
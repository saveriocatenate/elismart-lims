package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Master data for all laboratory reagents, including name, manufacturer, and optional description.
 */
@Entity
@Table(name = "reagent_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReagentCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String manufacturer;

    private String description;
}
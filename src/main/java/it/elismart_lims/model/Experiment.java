package it.elismart_lims.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An actual instance of a protocol run on the lab bench, with metadata
 * about the date and overall validation status.
 */
@Entity
@Table(name = "experiment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private Protocol protocol;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    @Builder.Default
    private List<UsedReagentBatch> usedReagentBatches = new ArrayList<>();

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MeasurementPair> measurementPairs = new ArrayList<>();
}
package it.elismart_lims.repository;

import it.elismart_lims.model.Experiment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * JPA repository for Experiment entities.
 *
 * <p>Two query patterns are supported without N+1 queries:</p>
 * <ul>
 *   <li><strong>Single lookup</strong> ({@link #findById}): one SQL query via JOIN FETCH for all
 *       three associations (protocol, reagent batches, measurement pairs).</li>
 *   <li><strong>Paginated search</strong> ({@link #findAll(Specification, Pageable)}): one SQL query
 *       with a JOIN FETCH for {@code protocol} (a ManyToOne; safe with LIMIT/OFFSET), plus two
 *       Hibernate batch-fetch queries for the two OneToMany collections, driven by the
 *       {@code @BatchSize(size = 50)} annotations on the entity fields. Total: 3 queries for any
 *       page size up to 50, versus 1 + 2N without these annotations.</li>
 * </ul>
 */
public interface ExperimentRepository extends JpaRepository<Experiment, Long>, JpaSpecificationExecutor<Experiment> {

    /**
     * Fetch a single experiment with all associations loaded in one query.
     * Safe to use with JOIN FETCH for collections because no pagination is involved.
     */
    @Override
    @EntityGraph(attributePaths = {"protocol", "usedReagentBatches", "measurementPairs"})
    Optional<Experiment> findById(Long id);

    /**
     * Paginated search with dynamic filters.
     * Only {@code protocol} (ManyToOne) is JOIN-fetched here; fetching collections via JOIN FETCH
     * with a paginated query would force in-memory pagination (HHH90003004). The OneToMany
     * collections are instead loaded in two batch queries driven by {@code @BatchSize} on the entity.
     */
    @Override
    @EntityGraph(attributePaths = {"protocol"})
    Page<Experiment> findAll(Specification<Experiment> spec, Pageable pageable);

    /**
     * Check whether any experiment is linked to the given protocol.
     * Used to guard protocol deletion and modification.
     *
     * @param protocolId the protocol ID to check
     * @return {@code true} if at least one experiment references this protocol
     */
    boolean existsByProtocolId(Long protocolId);
}

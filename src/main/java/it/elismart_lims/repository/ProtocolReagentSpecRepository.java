package it.elismart_lims.repository;

import it.elismart_lims.model.ProtocolReagentSpec;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for ProtocolReagentSpec entities.
 */
public interface ProtocolReagentSpecRepository extends JpaRepository<ProtocolReagentSpec, Long> {

    /**
     * Find all reagent specifications for a given protocol, eagerly fetching the
     * associated {@link it.elismart_lims.model.ReagentCatalog} in a single JOIN.
     *
     * <p>{@code @EntityGraph} prevents the N+1 query problem that would otherwise
     * occur when iterating over the returned list and accessing the lazy
     * {@code reagent} association on each element.</p>
     *
     * @param protocolId the protocol whose reagent specs are requested
     * @return all specs for the given protocol with {@code reagent} pre-loaded
     */
    @EntityGraph(attributePaths = {"reagent"})
    List<ProtocolReagentSpec> findByProtocolId(Long protocolId);
}
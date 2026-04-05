package it.elismart_lims.repository;

import it.elismart_lims.model.ProtocolReagentSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for ProtocolReagentSpec entities.
 */
public interface ProtocolReagentSpecRepository extends JpaRepository<ProtocolReagentSpec, Long> {

    /**
     * Find all reagent specifications for a given protocol.
     */
    List<ProtocolReagentSpec> findByProtocolId(Long protocolId);
}
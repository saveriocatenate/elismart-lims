package it.elismart_lims.repository;

import it.elismart_lims.model.Protocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for Protocol entities.
 */
public interface ProtocolRepository extends JpaRepository<Protocol, Long> {

    /**
     * Find a protocol by its unique name.
     *
     * @param name the protocol name
     * @return an Optional containing the Protocol if found
     */
    Optional<Protocol> findByName(String name);
}

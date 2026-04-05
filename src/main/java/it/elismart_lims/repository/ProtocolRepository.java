package it.elismart_lims.repository;

import it.elismart_lims.model.Protocol;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for Protocol entities.
 */
public interface ProtocolRepository extends JpaRepository<Protocol, Long> {
}

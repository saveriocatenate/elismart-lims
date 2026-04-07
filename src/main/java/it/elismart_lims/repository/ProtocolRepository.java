package it.elismart_lims.repository;

import it.elismart_lims.model.Protocol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Check whether a protocol with the same name (case-insensitive), number of calibration
     * pairs, and number of control pairs already exists.
     * Used to prevent creation of functionally duplicate protocols.
     *
     * @param name                  the protocol name to check
     * @param numCalibrationPairs   the calibration pair count to check
     * @param numControlPairs       the control pair count to check
     * @return {@code true} if a matching protocol exists
     */
    boolean existsByNameIgnoreCaseAndNumCalibrationPairsAndNumControlPairs(
            String name, Integer numCalibrationPairs, Integer numControlPairs);

    /**
     * Find all protocols whose name contains the given string, case-insensitively, with pagination.
     *
     * @param name     the partial name to search for
     * @param pageable pagination and sorting information
     * @return a page of matching Protocol entities
     */
    Page<Protocol> findByNameContainingIgnoreCase(String name, Pageable pageable);
}

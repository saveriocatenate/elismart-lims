package it.elismart_lims.repository;

import it.elismart_lims.model.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for {@link Sample} entities.
 */
public interface SampleRepository extends JpaRepository<Sample, Long> {

    /**
     * Find a sample by its unique barcode.
     *
     * @param barcode the barcode to look up
     * @return an {@link Optional} containing the matching sample, or empty if none exists
     */
    Optional<Sample> findByBarcode(String barcode);

    /**
     * Check whether a sample with the given barcode already exists.
     *
     * @param barcode the barcode to check
     * @return {@code true} if a matching sample exists
     */
    boolean existsByBarcode(String barcode);
}

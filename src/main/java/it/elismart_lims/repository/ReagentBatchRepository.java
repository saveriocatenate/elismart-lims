package it.elismart_lims.repository;

import it.elismart_lims.model.ReagentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ReagentBatch} entities.
 */
public interface ReagentBatchRepository extends JpaRepository<ReagentBatch, Long> {

    /**
     * Returns all batches belonging to the given reagent catalog entry.
     *
     * @param reagentId the reagent catalog ID
     * @return list of matching batches, possibly empty
     */
    List<ReagentBatch> findByReagentId(Long reagentId);

    /**
     * Finds a specific batch by reagent catalog ID and lot number.
     *
     * @param reagentId the reagent catalog ID
     * @param lotNumber the lot number to match
     * @return an {@link Optional} containing the batch if found
     */
    Optional<ReagentBatch> findByReagentIdAndLotNumber(Long reagentId, String lotNumber);
}

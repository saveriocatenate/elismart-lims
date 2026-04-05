package it.elismart_lims.repository;

import it.elismart_lims.model.UsedReagentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for UsedReagentBatch entities.
 */
public interface UsedReagentBatchRepository extends JpaRepository<UsedReagentBatch, Long> {
}
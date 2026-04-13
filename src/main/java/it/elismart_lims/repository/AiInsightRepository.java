package it.elismart_lims.repository;

import it.elismart_lims.model.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AiInsight} entities.
 *
 * <p>Owned exclusively by {@link it.elismart_lims.service.AiInsightService}.</p>
 */
public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    /**
     * Returns all insights that include the given experiment, ordered by
     * {@code generatedAt} descending (most recent first).
     *
     * @param experimentId the experiment primary key
     * @return the matching insight list, newest first
     */
    List<AiInsight> findByExperimentsIdOrderByGeneratedAtDesc(Long experimentId);
}

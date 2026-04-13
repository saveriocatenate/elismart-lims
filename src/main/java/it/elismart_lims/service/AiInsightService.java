package it.elismart_lims.service;

import it.elismart_lims.dto.AiInsightResponse;
import it.elismart_lims.mapper.AiInsightMapper;
import it.elismart_lims.model.AiInsight;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.repository.AiInsightRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for persisting and querying {@link AiInsight} records.
 *
 * <p>Each call to {@link #save} creates an immutable insight record in the database,
 * linking the AI response to the experiments that were analyzed and the user who
 * triggered the analysis.</p>
 *
 * <p>Experiment entity references are obtained via {@link EntityManager#getReference} to
 * avoid loading full experiment graphs — only the FK values are needed for the join table.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightService {

    private final AiInsightRepository aiInsightRepository;

    /**
     * JPA EntityManager used exclusively to obtain lightweight proxy references to
     * {@link Experiment} entities for the ManyToMany join table. No repository from
     * another domain is injected.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Persists a new AI insight linking the given analysis to its source experiments.
     *
     * @param userQuestion  the analyst's question
     * @param aiResponse    the AI-generated response text
     * @param generatedBy   username of the principal who triggered the analysis
     * @param experimentIds IDs of the experiments included in the analysis
     * @return the saved insight as a response DTO
     */
    @Transactional
    public AiInsightResponse save(String userQuestion, String aiResponse,
                                   String generatedBy, List<Long> experimentIds) {
        List<Experiment> experiments = experimentIds.stream()
                .map(id -> entityManager.getReference(Experiment.class, id))
                .toList();

        AiInsight insight = AiInsight.builder()
                .userQuestion(userQuestion)
                .aiResponse(aiResponse)
                .generatedAt(LocalDateTime.now())
                .generatedBy(generatedBy)
                .experiments(experiments)
                .build();

        AiInsight saved = aiInsightRepository.save(insight);
        log.info("Persisted AiInsight id={} for {} experiment(s) by '{}'",
                saved.getId(), experimentIds.size(), generatedBy);
        return AiInsightMapper.toResponse(saved);
    }

    /**
     * Returns all insights that include the given experiment, ordered newest first.
     *
     * @param experimentId the experiment primary key
     * @return insight list, ordered by {@code generatedAt} descending
     */
    @Transactional(readOnly = true)
    public List<AiInsightResponse> getByExperimentId(Long experimentId) {
        List<AiInsight> insights =
                aiInsightRepository.findByExperimentsIdOrderByGeneratedAtDesc(experimentId);
        return AiInsightMapper.toResponseList(insights);
    }
}

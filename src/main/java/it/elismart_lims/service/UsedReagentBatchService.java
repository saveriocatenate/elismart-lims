package it.elismart_lims.service;

import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.UsedReagentBatchUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.UsedReagentBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for {@link UsedReagentBatch} operations.
 *
 * <p>Each entry links a pre-registered {@link ReagentBatch} to an {@link Experiment}
 * for lot traceability.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedReagentBatchService {

    private final UsedReagentBatchRepository usedReagentBatchRepository;
    private final ReagentBatchService reagentBatchService;

    /**
     * Find a used reagent batch entity by ID.
     *
     * @param id the batch link ID
     * @return the found {@link UsedReagentBatch}
     * @throws ResourceNotFoundException if no entry exists with the given ID
     */
    @Transactional(readOnly = true)
    public UsedReagentBatch getById(Long id) {
        return usedReagentBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Used reagent batch not found with id: " + id));
    }

    /**
     * Create and persist all reagent batch links for the given experiment.
     *
     * <p>Each request must reference a valid {@link ReagentBatch}. The denormalised
     * {@code reagent} field on {@link UsedReagentBatch} is populated from
     * {@link ReagentBatch#getReagent()} at save time.</p>
     *
     * @param requests   the list of batch link requests
     * @param experiment the experiment these entries belong to
     * @return the list of persisted {@link UsedReagentBatch} entities
     * @throws ResourceNotFoundException if any referenced {@link ReagentBatch} does not exist
     */
    @Transactional
    public List<UsedReagentBatch> createAllForExperiment(List<UsedReagentBatchRequest> requests, Experiment experiment) {
        log.debug("Creating {} reagent batch link(s) for experiment id: {}", requests.size(), experiment.getId());
        List<UsedReagentBatch> saved = requests.stream()
                .map(req -> {
                    ReagentBatch batch = reagentBatchService.getEntityById(req.reagentBatchId());
                    return usedReagentBatchRepository.save(
                            UsedReagentBatch.builder()
                                    .experiment(experiment)
                                    .reagent(batch.getReagent())
                                    .reagentBatch(batch)
                                    .build());
                })
                .toList();
        log.debug("Saved {} reagent batch link(s) for experiment id: {}", saved.size(), experiment.getId());
        return saved;
    }

    /**
     * Re-link an existing {@link UsedReagentBatch} to a different {@link ReagentBatch}.
     *
     * <p>The linked experiment is immutable. The denormalised {@code reagent} field is
     * updated to match the new batch's reagent.</p>
     *
     * @param request      the update payload carrying the link ID and new batch ID
     * @param experimentId the ID of the owning experiment, used to guard cross-experiment updates
     * @throws ResourceNotFoundException if no link or batch exists with the given IDs
     * @throws IllegalArgumentException  if the link does not belong to {@code experimentId}
     */
    @Transactional
    public void updateBatch(UsedReagentBatchUpdateRequest request, Long experimentId) {
        UsedReagentBatch link = usedReagentBatchRepository.findById(request.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Used reagent batch not found with id: " + request.id()));

        if (!link.getExperiment().getId().equals(experimentId)) {
            throw new IllegalArgumentException(
                    "Batch " + request.id() + " does not belong to experiment " + experimentId);
        }

        ReagentBatch newBatch = reagentBatchService.getEntityById(request.reagentBatchId());
        link.setReagentBatch(newBatch);
        link.setReagent(newBatch.getReagent());
        usedReagentBatchRepository.save(link);
        log.debug("Updated reagent batch link id {} for experiment id {}", request.id(), experimentId);
    }
}

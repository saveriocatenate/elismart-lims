package it.elismart_lims.service;

import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.UsedReagentBatchUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.UsedReagentBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for UsedReagentBatch operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedReagentBatchService {

    private final UsedReagentBatchRepository usedReagentBatchRepository;
    private final ReagentCatalogService reagentCatalogService;

    /**
     * Find a used reagent batch by ID.
     *
     * @param id the batch ID
     * @return the found UsedReagentBatch
     * @throws ResourceNotFoundException if no batch exists with the given ID
     */
    @Transactional(readOnly = true)
    public UsedReagentBatch getById(Long id) {
        return usedReagentBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Used reagent batch not found with id: " + id));
    }

    /**
     * Create and persist all reagent batches for the given experiment.
     * Each request is resolved to its ReagentCatalog entity and saved with the experiment reference.
     *
     * @param requests   the list of batch creation requests
     * @param experiment the experiment these batches belong to
     * @return the list of persisted UsedReagentBatch entities
     */
    @Transactional
    public List<UsedReagentBatch> createAllForExperiment(List<UsedReagentBatchRequest> requests, Experiment experiment) {
        log.debug("Creating {} reagent batch(es) for experiment id: {}", requests.size(), experiment.getId());
        List<UsedReagentBatch> saved = requests.stream()
                .map(req -> {
                    var reagent = reagentCatalogService.getEntityById(req.reagentId());
                    return usedReagentBatchRepository.save(
                            UsedReagentBatch.builder()
                                    .experiment(experiment)
                                    .reagent(reagent)
                                    .lotNumber(req.lotNumber())
                                    .expiryDate(req.expiryDate())
                                    .build());
                })
                .toList();
        log.debug("Saved {} reagent batch(es) for experiment id: {}", saved.size(), experiment.getId());
        return saved;
    }

    /**
     * Update the mutable fields of an existing {@link UsedReagentBatch}.
     *
     * <p>Only {@code lotNumber} and {@code expiryDate} are changed.
     * The linked reagent and experiment are immutable.</p>
     *
     * @param request      the update payload carrying the batch ID and new field values
     * @param experimentId the ID of the owning experiment, used to guard against cross-experiment updates
     * @throws ResourceNotFoundException if no batch exists with the given ID
     * @throws IllegalArgumentException  if the batch does not belong to {@code experimentId}
     */
    @Transactional
    public void updateBatch(UsedReagentBatchUpdateRequest request, Long experimentId) {
        UsedReagentBatch batch = usedReagentBatchRepository.findById(request.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Used reagent batch not found with id: " + request.id()));

        if (!batch.getExperiment().getId().equals(experimentId)) {
            throw new IllegalArgumentException(
                    "Batch " + request.id() + " does not belong to experiment " + experimentId);
        }

        batch.setLotNumber(request.lotNumber());
        batch.setExpiryDate(request.expiryDate());
        usedReagentBatchRepository.save(batch);
        log.debug("Updated reagent batch id {} for experiment id {}", request.id(), experimentId);
    }
}

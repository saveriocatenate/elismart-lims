package it.elismart_lims.service;

import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.UsedReagentBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for UsedReagentBatch operations.
 */
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
        return requests.stream()
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
    }
}

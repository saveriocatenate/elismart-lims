package it.elismart_lims.service;

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
     * Link existing reagent batches to an experiment by setting the experiment reference and saving.
     *
     * @param usedReagentBatchIds the IDs of the reagent batches to link
     * @param experiment the experiment to link them to
     * @return the updated list of UsedReagentBatch entities
     */
    @Transactional
    public List<UsedReagentBatch> linkToExperiment(List<Long> usedReagentBatchIds, Experiment experiment) {
        return usedReagentBatchIds.stream()
                .map(id -> {
                    UsedReagentBatch batch = getById(id);
                    batch.setExperiment(experiment);
                    usedReagentBatchRepository.save(batch);
                    return batch;
                })
                .toList();
    }

    /**
     * Collect the set of reagent IDs covered by the given batch IDs.
     */
    @Transactional(readOnly = true)
    public List<Long> getReagentIdsByBatchIds(List<Long> batchIds) {
        return batchIds.stream()
                .map(this::getById)
                .map(batch -> batch.getReagent().getId())
                .toList();
    }
}

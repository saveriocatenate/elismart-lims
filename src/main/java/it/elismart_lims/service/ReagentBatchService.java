package it.elismart_lims.service;

import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.dto.ReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ReagentBatchMapper;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.repository.ReagentBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for {@link ReagentBatch} operations.
 *
 * <p>Each physical reagent lot is registered once and can be referenced by multiple
 * experiments to avoid duplicating lot-traceability data.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentBatchService {

    private final ReagentBatchRepository reagentBatchRepository;
    private final ReagentCatalogService reagentCatalogService;

    /**
     * Registers a new reagent batch.
     *
     * <p>Verifies that the referenced {@code reagentId} exists before persisting.
     * Rejects the insert if a batch with the same {@code reagentId} and {@code lotNumber}
     * already exists (the unique constraint at DB level also enforces this).</p>
     *
     * @param request the validated create request
     * @return the saved batch as a response DTO
     * @throws ResourceNotFoundException if the referenced reagent catalog entry does not exist
     * @throws IllegalArgumentException  if a batch with the same reagent/lot combination exists
     */
    @Transactional
    public ReagentBatchResponse create(ReagentBatchCreateRequest request) {
        var reagent = reagentCatalogService.getEntityById(request.reagentId());

        if (reagentBatchRepository.findByReagentIdAndLotNumber(request.reagentId(), request.lotNumber()).isPresent()) {
            throw new IllegalArgumentException(
                    "A batch with lot number '" + request.lotNumber()
                    + "' already exists for reagent id " + request.reagentId());
        }

        ReagentBatch batch = ReagentBatchMapper.toEntity(request, reagent);
        ReagentBatch saved = reagentBatchRepository.save(batch);
        log.info("ReagentBatch created: id={}, reagentId={}, lot={}", saved.getId(), request.reagentId(), request.lotNumber());
        return ReagentBatchMapper.toResponse(saved);
    }

    /**
     * Returns all batches registered for the given reagent catalog entry.
     *
     * @param reagentId the reagent catalog ID
     * @return list of response DTOs, possibly empty
     */
    @Transactional(readOnly = true)
    public List<ReagentBatchResponse> findByReagentId(Long reagentId) {
        return ReagentBatchMapper.toResponseList(reagentBatchRepository.findByReagentId(reagentId));
    }

    /**
     * Returns a single batch by its primary key.
     *
     * @param id the batch ID
     * @return the response DTO
     * @throws ResourceNotFoundException if no batch exists with the given ID
     */
    @Transactional(readOnly = true)
    public ReagentBatchResponse findById(Long id) {
        return ReagentBatchMapper.toResponse(getEntityById(id));
    }

    /**
     * Returns a single batch entity by its primary key.
     *
     * @param id the batch ID
     * @return the entity
     * @throws ResourceNotFoundException if no batch exists with the given ID
     */
    @Transactional(readOnly = true)
    public ReagentBatch getEntityById(Long id) {
        return reagentBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReagentBatch not found with id: " + id));
    }
}

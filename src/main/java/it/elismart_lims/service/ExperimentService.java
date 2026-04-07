package it.elismart_lims.service;

import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.dto.ExperimentUpdateRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ExperimentMapper;
import it.elismart_lims.mapper.MeasurementPairMapper;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.ExperimentRepository;
import it.elismart_lims.util.ExperimentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for Experiment operations.
 * Delegates to other services for cross-domain data access (never injects other repositories).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ProtocolService protocolService;
    private final UsedReagentBatchService usedReagentBatchService;
    private final MeasurementPairService measurementPairService;
    private final ProtocolReagentSpecService protocolReagentSpecService;

    /**
     * Find an experiment by its ID.
     */
    @Transactional(readOnly = true)
    public ExperimentResponse getById(Long id) {
        var experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + id));
        return ExperimentMapper.toResponse(experiment);
    }

    /**
     * Create a new experiment with its reagent batches and measurement pairs atomically.
     * Validates that all mandatory reagents for the protocol are covered by the provided batches.
     *
     * @param request the experiment creation payload
     * @return the created ExperimentResponse DTO
     * @throws it.elismart_lims.exception.model.ResourceNotFoundException if the referenced protocol does not exist
     * @throws ProtocolMismatchException if mandatory reagents are missing from the provided batches
     */
    @Transactional
    public ExperimentResponse create(ExperimentRequest request) {
        log.info("Creating experiment '{}' for protocol id: {}", request.name(), request.protocolId());
        var protocol = protocolService.getEntityById(request.protocolId());

        validateReagentBatches(request.usedReagentBatches(), protocol.getId());

        Experiment experiment = ExperimentMapper.toEntity(request, protocol);
        experiment = experimentRepository.save(experiment);

        List<UsedReagentBatch> batches = usedReagentBatchService
                .createAllForExperiment(request.usedReagentBatches(), experiment);
        experiment.setUsedReagentBatches(batches);

        List<MeasurementPair> pairs = MeasurementPairMapper.toEntityList(request.measurementPairs(), experiment);
        measurementPairService.saveAll(pairs);
        experiment.setMeasurementPairs(pairs);

        log.info("Experiment created with id: {}", experiment.getId());
        return ExperimentMapper.toResponse(experiment);
    }

    /**
     * Validate that the provided reagent batch requests cover all mandatory reagents of the protocol.
     *
     * @param batchRequests the inline batch requests from the experiment creation request
     * @param protocolId    the protocol whose mandatory reagents must be satisfied
     * @throws ProtocolMismatchException if any mandatory reagent is not covered
     */
    private void validateReagentBatches(List<UsedReagentBatchRequest> batchRequests, Long protocolId) {
        Set<Long> batchReagentIds = batchRequests.stream()
                .map(UsedReagentBatchRequest::reagentId)
                .collect(Collectors.toSet());

        Set<Long> mandatoryReagentIds = protocolReagentSpecService.getMandatoryReagentIds(protocolId);

        if (!batchReagentIds.containsAll(mandatoryReagentIds)) {
            log.warn("Reagent validation failed for protocol id: {}. Missing reagent IDs: {}",
                    protocolId, mandatoryReagentIds);
            throw new ProtocolMismatchException(
                    "Experiment must include all mandatory reagents defined by the protocol. " +
                    "Missing reagent IDs: " + mandatoryReagentIds +
                    ". Batch reagent IDs provided: " + batchReagentIds);
        }
    }

    /**
     * Update the mutable fields of an existing experiment.
     *
     * <p>The protocol and the set of linked reagents cannot be changed after creation.
     * Only {@code name}, {@code date}, {@code status}, and per-batch lot details are
     * mutable via this operation.</p>
     *
     * @param id      the experiment ID
     * @param request the update payload
     * @return the updated ExperimentResponse DTO
     * @throws it.elismart_lims.exception.model.ResourceNotFoundException if no experiment exists with the given ID
     */
    @Transactional
    public ExperimentResponse update(Long id, ExperimentUpdateRequest request) {
        log.info("Updating experiment id: {}", id);
        var experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + id));

        experiment.setName(request.name());
        experiment.setDate(request.date());
        experiment.setStatus(request.status());

        for (var batchUpdate : request.reagentBatchUpdates()) {
            usedReagentBatchService.updateBatch(batchUpdate, id);
        }

        if (request.measurementPairUpdates() != null) {
            for (var pairUpdate : request.measurementPairUpdates()) {
                measurementPairService.update(pairUpdate, id);
            }
        }

        experiment = experimentRepository.save(experiment);
        log.info("Experiment updated id: {}", id);
        return ExperimentMapper.toResponse(experiment);
    }

    /**
     * Check whether any experiment is linked to the given protocol.
     * Used by {@link ProtocolService} to guard deletion and updates.
     *
     * @param protocolId the protocol ID to check
     * @return {@code true} if at least one experiment references this protocol
     */
    @Transactional(readOnly = true)
    public boolean existsByProtocolId(Long protocolId) {
        return experimentRepository.existsByProtocolId(protocolId);
    }

    /**
     * Delete an experiment by its ID, cascading to associated reagent batches and measurement pairs.
     *
     * @param id the experiment ID
     * @throws it.elismart_lims.exception.model.ResourceNotFoundException if no experiment exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting experiment id: {}", id);
        if (!experimentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Experiment not found with id: " + id);
        }
        experimentRepository.deleteById(id);
        log.info("Experiment deleted id: {}", id);
    }

    /**
     * Search experiments with optional filters and pagination.
     *
     * @param request the search criteria and pagination parameters
     * @return a paginated {@link ExperimentPage} matching the filters
     */
    @Transactional(readOnly = true)
    public ExperimentPage search(ExperimentSearchRequest request) {
        PageRequest pageRequest = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(Sort.Direction.DESC, "date"));

        Page<Experiment> page = experimentRepository.findAll(
                ExperimentSpecifications.buildSpecification(request), pageRequest);

        List<ExperimentResponse> content = page.getContent().stream()
                .map(ExperimentMapper::toResponse)
                .toList();

        return new ExperimentPage(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}

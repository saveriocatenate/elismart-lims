package it.elismart_lims.service;

import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
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
     */
    @Transactional
    public ExperimentResponse create(ExperimentRequest request) {
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
            throw new ProtocolMismatchException(
                    "Experiment must include all mandatory reagents defined by the protocol. " +
                    "Missing reagent IDs: " + mandatoryReagentIds +
                    ". Batch reagent IDs provided: " + batchReagentIds);
        }
    }

    /**
     * Delete an experiment by its ID, cascading to associated reagent batches and measurement pairs.
     */
    @Transactional
    public void delete(Long id) {
        if (!experimentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Experiment not found with id: " + id);
        }
        experimentRepository.deleteById(id);
    }

    /**
     * Search experiments with optional filters and pagination.
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

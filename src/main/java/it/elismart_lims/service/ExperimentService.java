package it.elismart_lims.service;

import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Create a new experiment with its associated reagent batches and measurement pairs.
     * Validates that all mandatory reagents for the protocol are present.
     */
    @Transactional
    public ExperimentResponse create(ExperimentRequest request) {
        var protocol = protocolService.getEntityById(request.protocolId());

        // Validate that used reagent batches cover all mandatory protocol reagents
        validateReagentBatches(request.usedReagentBatchIds(), protocol.getId());

        // Create and save the experiment
        Experiment experiment = ExperimentMapper.toEntity(request, protocol);
        experiment = experimentRepository.save(experiment);

        // Link used reagent batches to experiment
        List<UsedReagentBatch> batches = usedReagentBatchService
                .linkToExperiment(request.usedReagentBatchIds(), experiment);
        experiment.setUsedReagentBatches(batches);

        // Create and persist measurement pairs
        List<MeasurementPair> pairs = MeasurementPairMapper.toEntityList(request.measurementPairs(), experiment);
        measurementPairService.saveAll(pairs);
        experiment.setMeasurementPairs(pairs);

        return ExperimentMapper.toResponse(experiment);
    }

    private void validateReagentBatches(List<Long> reagentBatchIds, Long protocolId) {
        Set<Long> batchReagentIds = new HashSet<>(
                usedReagentBatchService.getReagentIdsByBatchIds(reagentBatchIds));

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

package it.elismart_lims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.ExperimentRepository;
import it.elismart_lims.service.audit.AuditLogService;
import it.elismart_lims.service.curve.CalibrationPoint;
import it.elismart_lims.service.curve.CurveFittingService;
import it.elismart_lims.service.curve.CurveParameters;
import it.elismart_lims.service.validation.OutlierDetectionService;
import it.elismart_lims.service.validation.ValidationEngine;
import it.elismart_lims.service.validation.ValidationResult;
import it.elismart_lims.util.ExperimentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
    private final AuditLogService auditLogService;
    private final CurveFittingService curveFittingService;
    private final OutlierDetectionService outlierDetectionService;
    private final ValidationEngine validationEngine;
    private final ObjectMapper objectMapper;

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
     * <p>Every field that actually changes produces an {@link it.elismart_lims.model.AuditLog}
     * entry via {@link AuditLogService}. Fields whose old and new values are equal generate
     * no audit row.</p>
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

        auditIfChanged("Experiment", id, "name",       experiment.getName(),   request.name());
        auditIfChanged("Experiment", id, "date",       experiment.getDate(),   request.date());
        auditIfChanged("Experiment", id, "status",     experiment.getStatus(), request.status());

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
     * Runs the full validation workflow for an experiment:
     * <ol>
     *   <li>Fits the calibration curve from CALIBRATION pairs.</li>
     *   <li>Runs {@link OutlierDetectionService#detectOutliers} to auto-flag pairs whose
     *       %CV exceeds the protocol limit or that are statistical outliers (Grubbs test).
     *       Each newly flagged pair is audited with reason {@code "SYSTEM:outlier-detection"}.</li>
     *   <li>Back-interpolates concentrations for every non-outlier CONTROL/SAMPLE pair.</li>
     *   <li>Evaluates %CV and %Recovery against the protocol limits.</li>
     *   <li>Updates the experiment status to OK or KO.</li>
     * </ol>
     *
     * <p>The fitted {@link CurveParameters} are serialised as JSON and stored on
     * {@link Experiment#getCurveParameters()} for auditing and future display.</p>
     *
     * <p>An experiment already in a terminal state ({@link ExperimentStatus#OK} or
     * {@link ExperimentStatus#KO}) cannot be re-validated until its status is reset
     * to {@link ExperimentStatus#PENDING}.</p>
     *
     * @param id the experiment ID to validate
     * @return the updated {@link ExperimentResponse}
     * @throws ResourceNotFoundException if no experiment exists with the given ID
     * @throws IllegalStateException     if the experiment is already in a terminal status
     * @throws IllegalArgumentException  if no CALIBRATION pairs are present
     */
    @Transactional
    public ExperimentResponse validate(Long id) {
        log.info("Validating experiment id: {}", id);
        var experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + id));

        ExperimentStatus currentStatus = experiment.getStatus();
        if (currentStatus == ExperimentStatus.OK || currentStatus == ExperimentStatus.KO) {
            throw new IllegalStateException(
                    "Experiment id=" + id + " is already in terminal status " + currentStatus
                    + ". Reset to PENDING before re-validating.");
        }

        Protocol protocol = experiment.getProtocol();

        List<CalibrationPoint> calibrationPoints = experiment.getMeasurementPairs().stream()
                .filter(p -> p.getPairType() == PairType.CALIBRATION)
                .map(p -> new CalibrationPoint(p.getConcentrationNominal(), p.getSignalMean()))
                .toList();

        if (calibrationPoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Experiment id=" + id + " has no CALIBRATION pairs. Cannot fit calibration curve.");
        }

        CurveParameters curveParams = curveFittingService.fitCurve(protocol.getCurveType(), calibrationPoints);

        try {
            experiment.setCurveParameters(objectMapper.writeValueAsString(curveParams));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise curve parameters: " + e.getMessage(), e);
        }

        // Outlier detection — must run before ValidationEngine so flagged pairs are excluded.
        List<Long> outlierIds = outlierDetectionService.detectOutliers(
                experiment.getMeasurementPairs(), protocol);
        for (MeasurementPair pair : experiment.getMeasurementPairs()) {
            if (outlierIds.contains(pair.getId()) && !Boolean.TRUE.equals(pair.getIsOutlier())) {
                auditLogService.logChange("MeasurementPair", pair.getId(),
                        "isOutlier", "false", "true", "SYSTEM:outlier-detection");
                pair.setIsOutlier(true);
            }
        }
        if (!outlierIds.isEmpty()) {
            log.info("Experiment id={} — {} pair(s) auto-flagged as outliers: {}",
                    id, outlierIds.size(), outlierIds);
        }

        ValidationResult result = validationEngine.evaluate(experiment, protocol, curveParams);

        ExperimentStatus newStatus = result.overallStatus();
        auditIfChanged("Experiment", id, "status", currentStatus, newStatus);
        experiment.setStatus(newStatus);

        experiment = experimentRepository.save(experiment);
        log.info("Experiment id={} validated → status={}", id, newStatus);
        return ExperimentMapper.toResponse(experiment);
    }

    /**
     * Logs a field change via {@link AuditLogService} only when the old and new values differ.
     *
     * @param entityType simple class name of the entity
     * @param id         primary key of the entity row
     * @param field      Java field name
     * @param oldVal     value before the change; converted to String via {@link Object#toString()}
     * @param newVal     value after the change; converted to String via {@link Object#toString()}
     */
    private void auditIfChanged(String entityType, Long id, String field, Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            auditLogService.logChange(entityType, id, field,
                    oldVal != null ? oldVal.toString() : null,
                    newVal != null ? newVal.toString() : null,
                    null);
        }
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

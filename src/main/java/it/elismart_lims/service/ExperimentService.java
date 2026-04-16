package it.elismart_lims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.CsvImportConfig;
import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.MeasurementPairRequest;
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
import it.elismart_lims.service.io.CsvImportService;
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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    private final ReagentBatchService reagentBatchService;
    private final MeasurementPairService measurementPairService;
    private final ProtocolReagentSpecService protocolReagentSpecService;
    private final AuditLogService auditLogService;
    private final CurveFittingService curveFittingService;
    private final OutlierDetectionService outlierDetectionService;
    private final ValidationEngine validationEngine;
    private final CsvImportService csvImportService;
    private final ObjectMapper objectMapper;

    /**
     * Statuses that can only be written by the internal validation engine.
     * Any attempt to set these via the client-facing update API is rejected with 400.
     */
    private static final Set<ExperimentStatus> ENGINE_ONLY_STATUSES =
            EnumSet.of(ExperimentStatus.OK, ExperimentStatus.KO, ExperimentStatus.VALIDATION_ERROR);

    /**
     * Status values that are considered terminal validated results.
     * Moving away from these states requires a written justification.
     */
    private static final Set<ExperimentStatus> TERMINAL_STATUSES =
            EnumSet.of(ExperimentStatus.OK, ExperimentStatus.KO);

    /**
     * Valid status transitions that a client may request via the update API.
     * Transitions that result in {@link #ENGINE_ONLY_STATUSES} are excluded —
     * those can only be set internally by the validation engine.
     */
    private static final Map<ExperimentStatus, Set<ExperimentStatus>> VALID_CLIENT_TRANSITIONS;

    static {
        VALID_CLIENT_TRANSITIONS = new EnumMap<>(ExperimentStatus.class);
        VALID_CLIENT_TRANSITIONS.put(ExperimentStatus.PENDING,
                EnumSet.of(ExperimentStatus.PENDING, ExperimentStatus.COMPLETED));
        VALID_CLIENT_TRANSITIONS.put(ExperimentStatus.COMPLETED,
                EnumSet.of(ExperimentStatus.PENDING));
        VALID_CLIENT_TRANSITIONS.put(ExperimentStatus.OK,
                EnumSet.of(ExperimentStatus.PENDING));
        VALID_CLIENT_TRANSITIONS.put(ExperimentStatus.KO,
                EnumSet.of(ExperimentStatus.PENDING));
        VALID_CLIENT_TRANSITIONS.put(ExperimentStatus.VALIDATION_ERROR,
                EnumSet.of(ExperimentStatus.PENDING));
    }

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

        if (request.status() != ExperimentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "ERR_INVALID_CREATION_STATUS: experiments must be created with status PENDING. "
                    + "Requested: " + request.status());
        }

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
     * Validate that the provided reagent batch links cover all mandatory reagents of the protocol.
     *
     * <p>Each {@link UsedReagentBatchRequest} carries a {@code reagentBatchId}. The reagent ID
     * is resolved by loading the corresponding {@link it.elismart_lims.model.ReagentBatch}.</p>
     *
     * @param batchRequests the batch link requests from the experiment creation payload
     * @param protocolId    the protocol whose mandatory reagents must be satisfied
     * @throws ProtocolMismatchException if any mandatory reagent is not covered
     */
    private void validateReagentBatches(List<UsedReagentBatchRequest> batchRequests, Long protocolId) {
        Set<Long> batchReagentIds = batchRequests.stream()
                .map(req -> reagentBatchService.getEntityById(req.reagentBatchId()).getReagent().getId())
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
     * @throws IllegalArgumentException if the status transition involves a terminal state
     *         ({@code OK} or {@code KO}) and the {@code reason} field is blank
     */
    @Transactional
    public ExperimentResponse update(Long id, ExperimentUpdateRequest request) {
        log.info("Updating experiment id: {}", id);
        var experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + id));

        auditIfChanged(id, "name", experiment.getName(), request.name());
        auditIfChanged(id, "date", experiment.getDate(), request.date());

        // Status transition validation.
        ExperimentStatus oldStatus = experiment.getStatus();
        ExperimentStatus newStatus = request.status();

        // Transition validation is only relevant when the status is actually changing.
        if (!Objects.equals(oldStatus, newStatus)) {
            // The client may never directly set engine-only statuses (OK, KO, VALIDATION_ERROR).
            if (ENGINE_ONLY_STATUSES.contains(newStatus)) {
                throw new IllegalArgumentException(
                        "ERR_INVALID_STATUS_TRANSITION: status " + newStatus
                        + " can only be set by the validation engine. "
                        + "Use POST /experiments/{id}/validate to trigger validation.");
            }

            // Enforce the state machine: only transitions listed in VALID_CLIENT_TRANSITIONS are allowed.
            Set<ExperimentStatus> allowed = VALID_CLIENT_TRANSITIONS.getOrDefault(oldStatus, Set.of());
            if (!allowed.contains(newStatus)) {
                throw new IllegalArgumentException(
                        "ERR_INVALID_STATUS_TRANSITION: cannot transition experiment from "
                        + oldStatus + " to " + newStatus + ". "
                        + "Allowed targets from " + oldStatus + ": " + allowed);
            }

            // Moving away from a terminal validated state (OK/KO) requires a written reason.
            if (TERMINAL_STATUSES.contains(oldStatus)
                    && (request.reason() == null || request.reason().isBlank())) {
                throw new IllegalArgumentException(
                        "ERR_REASON_REQUIRED: a reason is required when resetting a validated "
                        + "experiment (OK/KO) back to PENDING. Provide a non-blank 'reason' field.");
            }
            auditLogService.logChange("Experiment", id, "status",
                    oldStatus.toString(), newStatus.toString(), request.reason());
        }

        experiment.setName(request.name());
        experiment.setDate(request.date());
        experiment.setStatus(newStatus);

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

        CurveParameters curveParams;
        try {
            curveParams = curveFittingService.fitCurve(protocol.getCurveType(), calibrationPoints);
        } catch (IllegalStateException e) {
            // Non-linear fitter hit evaluation limit — curve did not converge.
            log.warn("Curve fitting failed for experiment id={}: {}", id, e.getMessage());
            auditIfChanged(id, "status", currentStatus, ExperimentStatus.VALIDATION_ERROR);
            experiment.setStatus(ExperimentStatus.VALIDATION_ERROR);
            experiment = experimentRepository.save(experiment);
            log.info("Experiment id={} → status=VALIDATION_ERROR (curve fitting non-convergence)", id);
            return ExperimentMapper.toResponse(experiment);
        }

        // Defensive guard: non-linear fitters include _convergence=1.0 on success.
        // _convergence=0.0 should never appear here (the fitter throws instead), but
        // if it ever does (e.g. future fitter variant), short-circuit to VALIDATION_ERROR.
        double convergenceFlag = curveParams.values()
                .getOrDefault(CurveParameters.META_CONVERGENCE, 1.0);
        if (convergenceFlag == 0.0) {
            log.warn("Experiment id={} — curve parameters carry _convergence=0, "
                    + "skipping validation and setting VALIDATION_ERROR", id);
            auditIfChanged(id, "status", currentStatus, ExperimentStatus.VALIDATION_ERROR);
            experiment.setStatus(ExperimentStatus.VALIDATION_ERROR);
            experiment = experimentRepository.save(experiment);
            return ExperimentMapper.toResponse(experiment);
        }

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
        auditIfChanged(id, "status", currentStatus, newStatus);
        experiment.setStatus(newStatus);

        experiment = experimentRepository.save(experiment);
        log.info("Experiment id={} validated → status={}", id, newStatus);
        return ExperimentMapper.toResponse(experiment);
    }

    /**
     * Logs a field change via {@link AuditLogService} only when the old and new values differ.
     *
     * @param id     primary key of the entity row
     * @param field  Java field name
     * @param oldVal value before the change; converted to String via {@link Object#toString()}
     * @param newVal value after the change; converted to String via {@link Object#toString()}
     */
    private void auditIfChanged(Long id, String field, Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            auditLogService.logChange("Experiment", id, field,
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
     * Parses the supplied CSV file and appends the resulting {@link MeasurementPair}s to the
     * experiment identified by {@code id}.
     *
     * <p>Parsing is delegated to {@link CsvImportService}. All derived fields
     * ({@code signalMean}, {@code cvPct}) are recalculated server-side after import via
     * {@link it.elismart_lims.mapper.MeasurementPairMapper}.</p>
     *
     * @param id     the target experiment ID
     * @param file   the uploaded CSV file; must not be empty
     * @param config import configuration (format, column names, well mapping)
     * @return the updated {@link ExperimentResponse} including all newly imported pairs
     * @throws ResourceNotFoundException if no experiment exists with the given ID
     * @throws IllegalArgumentException  if the file is empty, a required column is missing,
     *                                   no rows match the well mapping, or the stream cannot be read
     */
    @Transactional
    public ExperimentResponse importCsv(Long id, MultipartFile file, CsvImportConfig config) {
        var experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + id));

        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file must not be empty.");
        }

        List<MeasurementPairRequest> pairRequests;
        try {
            pairRequests = csvImportService.parse(file.getInputStream(), config);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read CSV file: " + e.getMessage(), e);
        }

        List<MeasurementPair> entities = MeasurementPairMapper.toEntityList(pairRequests, experiment);
        List<MeasurementPair> saved = measurementPairService.saveAll(entities);
        saved.forEach(experiment::addMeasurementPair);

        log.info("CSV import: {} pair(s) added to experiment id={}", saved.size(), id);
        return ExperimentMapper.toResponse(experiment);
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
     * <p>When {@link ExperimentSearchRequest#mine()} is {@code true}, results are restricted to
     * experiments whose {@code createdBy} equals the authenticated user's username. This lets
     * analysts working in a shared database quickly filter to their own work without affecting
     * admins or reviewers who need a global view.</p>
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

        String createdByFilter = null;
        if (request.mine()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                createdByFilter = auth.getName();
            }
        }

        Page<Experiment> page = experimentRepository.findAll(
                ExperimentSpecifications.buildSpecification(request, createdByFilter), pageRequest);

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

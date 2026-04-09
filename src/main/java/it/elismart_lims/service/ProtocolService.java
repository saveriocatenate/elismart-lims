package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ProtocolMapper;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
import it.elismart_lims.service.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Business logic for Protocol operations.
 */
@Slf4j
@Service
public class ProtocolService {

    private final ProtocolRepository protocolRepository;

    /**
     * Lazily injected to break the circular dependency with {@link ExperimentService}.
     * Spring resolves via a proxy on first use; Mockito injects the mock via constructor.
     */
    private final ExperimentService experimentService;

    private final AuditLogService auditLogService;

    /**
     * Constructor with {@code @Lazy} on the {@link ExperimentService} parameter to break
     * the circular dependency: ExperimentService → ProtocolService → ExperimentService.
     *
     * @param protocolRepository the protocol repository
     * @param experimentService  the experiment service (lazy proxy in production)
     * @param auditLogService    the audit log service for change tracking
     */
    public ProtocolService(
            ProtocolRepository protocolRepository,
            @Lazy ExperimentService experimentService,
            AuditLogService auditLogService) {
        this.protocolRepository = protocolRepository;
        this.experimentService = experimentService;
        this.auditLogService = auditLogService;
    }

    /**
     * Return all protocols.
     *
     * @return list of all ProtocolResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ProtocolResponse> getAll() {
        return protocolRepository.findAll().stream()
                .map(ProtocolMapper::toResponse)
                .toList();
    }

    /**
     * Find a protocol by its ID.
     */
    @Transactional(readOnly = true)
    public ProtocolResponse getById(Long id) {
        var protocol = getEntityById(id);
        return ProtocolMapper.toResponse(protocol);
    }

    /**
     * Find a protocol entity by its ID.
     * Used internally by other services to avoid cross-repository injection.
     *
     * @param id the protocol ID
     * @return the found Protocol entity
     * @throws ResourceNotFoundException if no protocol exists with the given ID
     */
    public Protocol getEntityById(Long id) {
        return protocolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with id: " + id));
    }

    /**
     * Create a new protocol.
     *
     * <p>Rejects the insert if a protocol with the same name (case-insensitive),
     * number of calibration pairs, and number of control pairs already exists.
     * This prevents the accidental creation of functionally duplicate protocols.</p>
     *
     * @param protocol the entity to persist
     * @return the created ProtocolResponse DTO
     * @throws IllegalArgumentException if a duplicate protocol is detected
     */
    @Transactional
    public ProtocolResponse create(Protocol protocol) {
        if (protocolRepository.existsByNameIgnoreCaseAndNumCalibrationPairsAndNumControlPairs(
                protocol.getName(),
                protocol.getNumCalibrationPairs(),
                protocol.getNumControlPairs())) {
            throw new IllegalArgumentException(
                    "A protocol named '" + protocol.getName()
                    + "' with " + protocol.getNumCalibrationPairs() + " calibration pairs"
                    + " and " + protocol.getNumControlPairs() + " control pairs already exists.");
        }
        log.info("Creating protocol: {}", protocol.getName());
        ProtocolResponse response = ProtocolMapper.toResponse(protocolRepository.save(protocol));
        log.info("Protocol created with id: {}", response.id());
        return response;
    }

    /**
     * Search protocols by partial name match (case-insensitive) with pagination.
     * Returns all protocols when {@code name} is {@code null} or blank.
     *
     * @param name     the partial name to search for; {@code null} or blank returns all
     * @param pageable pagination and sorting information
     * @return a page of matching ProtocolResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<ProtocolResponse> search(String name, Pageable pageable) {
        if (name == null || name.isBlank()) {
            return protocolRepository.findAll(pageable).map(ProtocolMapper::toResponse);
        }
        return protocolRepository.findByNameContainingIgnoreCase(name, pageable)
                .map(ProtocolMapper::toResponse);
    }

    /**
     * Update the fields of an existing protocol.
     *
     * <p>Blocked if any experiment already references this protocol — the user must
     * remove all linked experiments before editing the protocol.</p>
     *
     * <p>Every field that actually changes produces an {@link it.elismart_lims.model.AuditLog}
     * entry via {@link AuditLogService}. Fields whose old and new values are equal generate
     * no audit row.</p>
     *
     * @param id      the protocol ID
     * @param request the update payload
     * @return the updated ProtocolResponse DTO
     * @throws ResourceNotFoundException if no protocol exists with the given ID
     * @throws IllegalStateException     if experiments are linked to this protocol
     */
    @Transactional
    public ProtocolResponse update(Long id, ProtocolRequest request) {
        var protocol = protocolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with id: " + id));
        if (experimentService.existsByProtocolId(id)) {
            throw new IllegalStateException(
                    "Cannot edit protocol: remove all experiments linked to this protocol first.");
        }
        log.info("Updating protocol id: {}", id);

        auditIfChanged("Protocol", id, "name",                protocol.getName(),               request.name());
        auditIfChanged("Protocol", id, "numCalibrationPairs", protocol.getNumCalibrationPairs(), request.numCalibrationPairs());
        auditIfChanged("Protocol", id, "numControlPairs",     protocol.getNumControlPairs(),     request.numControlPairs());
        auditIfChanged("Protocol", id, "maxCvAllowed",        protocol.getMaxCvAllowed(),        request.maxCvAllowed());
        auditIfChanged("Protocol", id, "maxErrorAllowed",     protocol.getMaxErrorAllowed(),     request.maxErrorAllowed());
        auditIfChanged("Protocol", id, "curveType",           protocol.getCurveType(),           request.curveType());

        ProtocolMapper.updateEntity(protocol, request);
        ProtocolResponse response = ProtocolMapper.toResponse(protocolRepository.save(protocol));
        log.info("Protocol updated id: {}", response.id());
        return response;
    }

    /**
     * Logs a field change via {@link AuditLogService} only when the old and new values differ.
     *
     * @param entityType simple class name of the entity
     * @param id         primary key of the entity row
     * @param field      Java field name
     * @param oldVal     value before the change
     * @param newVal     value after the change
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
     * Find a protocol response by its unique name.
     *
     * @param name the protocol name
     * @return the ProtocolResponse DTO
     * @throws ResourceNotFoundException if no protocol exists with the given name
     */
    @Transactional(readOnly = true)
    public ProtocolResponse getByName(String name) {
        var protocol = protocolRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with name: " + name));
        return ProtocolMapper.toResponse(protocol);
    }

    /**
     * Delete a protocol by its ID.
     *
     * @param id the protocol ID
     * @throws ResourceNotFoundException if no protocol exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting protocol id: {}", id);
        if (!protocolRepository.existsById(id)) {
            throw new ResourceNotFoundException("Protocol not found with id: " + id);
        }
        if (experimentService.existsByProtocolId(id)) {
            throw new IllegalStateException(
                    "Cannot delete protocol: remove all experiments linked to this protocol first.");
        }
        protocolRepository.deleteById(id);
        log.info("Protocol deleted id: {}", id);
    }
}

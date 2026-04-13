package it.elismart_lims.service;

import it.elismart_lims.dto.SampleCreateRequest;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.dto.SampleUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.SampleMapper;
import it.elismart_lims.model.Sample;
import it.elismart_lims.repository.SampleRepository;
import it.elismart_lims.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Business logic for {@link Sample} CRUD operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;
    private final AuditLogService auditLogService;

    /**
     * Return all samples with pagination.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of {@link SampleResponse} DTOs
     */
    @Transactional(readOnly = true)
    public Page<SampleResponse> getAll(Pageable pageable) {
        return sampleRepository.findAll(pageable).map(SampleMapper::toResponse);
    }

    /**
     * Return a single sample by its primary key.
     *
     * @param id the sample ID
     * @return the {@link SampleResponse}
     * @throws ResourceNotFoundException if no sample exists with the given ID
     */
    @Transactional(readOnly = true)
    public SampleResponse getById(Long id) {
        return SampleMapper.toResponse(getEntityById(id));
    }

    /**
     * Return a single sample by its unique barcode.
     *
     * @param barcode the barcode to search for
     * @return the {@link SampleResponse}
     * @throws ResourceNotFoundException if no sample exists with the given barcode
     */
    @Transactional(readOnly = true)
    public SampleResponse getByBarcode(String barcode) {
        Sample sample = sampleRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sample not found with barcode: " + barcode));
        return SampleMapper.toResponse(sample);
    }

    /**
     * Return the raw {@link Sample} entity by its primary key.
     * Intended for internal use by other services that need to link a Sample.
     *
     * @param id the sample ID
     * @return the {@link Sample} entity
     * @throws ResourceNotFoundException if no sample exists with the given ID
     */
    @Transactional(readOnly = true)
    public Sample getEntityById(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sample not found with id: " + id));
    }

    /**
     * Create a new sample.
     *
     * @param request the creation payload
     * @return the persisted {@link SampleResponse}
     * @throws IllegalArgumentException if a sample with the same barcode already exists
     */
    @Transactional
    public SampleResponse create(SampleCreateRequest request) {
        if (sampleRepository.existsByBarcode(request.barcode())) {
            throw new IllegalArgumentException(
                    "A sample with barcode '" + request.barcode() + "' already exists.");
        }
        Sample entity = SampleMapper.toEntity(request);
        Sample saved = sampleRepository.save(entity);
        log.info("Created sample id={} barcode={}", saved.getId(), saved.getBarcode());
        return SampleMapper.toResponse(saved);
    }

    /**
     * Update mutable fields of an existing sample.
     *
     * <p>Each changed field produces an {@link it.elismart_lims.model.AuditLog} entry via
     * {@link AuditLogService}. The barcode is immutable after creation and cannot be changed
     * through this method.</p>
     *
     * @param id      the sample ID
     * @param request the update payload
     * @return the updated {@link SampleResponse}
     * @throws ResourceNotFoundException if no sample exists with the given ID
     */
    @Transactional
    public SampleResponse update(Long id, SampleUpdateRequest request) {
        Sample sample = getEntityById(id);

        if (request.matrixType() != null) {
            auditIfChanged("Sample", id, "matrixType", sample.getMatrixType(), request.matrixType());
            sample.setMatrixType(request.matrixType());
        }
        if (request.patientId() != null) {
            auditIfChanged("Sample", id, "patientId", sample.getPatientId(), request.patientId());
            sample.setPatientId(request.patientId());
        }
        if (request.studyId() != null) {
            auditIfChanged("Sample", id, "studyId", sample.getStudyId(), request.studyId());
            sample.setStudyId(request.studyId());
        }
        if (request.collectionDate() != null) {
            auditIfChanged("Sample", id, "collectionDate",
                    sample.getCollectionDate(), request.collectionDate());
            sample.setCollectionDate(request.collectionDate());
        }
        if (request.preparationMethod() != null) {
            auditIfChanged("Sample", id, "preparationMethod",
                    sample.getPreparationMethod(), request.preparationMethod());
            sample.setPreparationMethod(request.preparationMethod());
        }
        if (request.notes() != null) {
            auditIfChanged("Sample", id, "notes", sample.getNotes(), request.notes());
            sample.setNotes(request.notes());
        }

        Sample saved = sampleRepository.save(sample);
        log.info("Updated sample id={}", id);
        return SampleMapper.toResponse(saved);
    }

    /**
     * Delete a sample by its primary key.
     *
     * @param id the sample ID
     * @throws ResourceNotFoundException if no sample exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        if (!sampleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Sample not found with id: " + id);
        }
        sampleRepository.deleteById(id);
        log.info("Deleted sample id={}", id);
    }

    /**
     * Logs a field change only when the old and new values differ.
     *
     * @param entityType simple class name of the entity
     * @param id         primary key of the entity row
     * @param field      Java field name
     * @param oldVal     value before the change
     * @param newVal     value after the change
     */
    private void auditIfChanged(String entityType, Long id, String field,
                                Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            auditLogService.logChange(entityType, id, field,
                    oldVal != null ? oldVal.toString() : null,
                    newVal != null ? newVal.toString() : null,
                    null);
        }
    }
}

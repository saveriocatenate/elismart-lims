package it.elismart_lims.service;

import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.MeasurementPairUpdateRequest;
import it.elismart_lims.dto.OutlierUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.MeasurementPairMapper;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.repository.MeasurementPairRepository;
import it.elismart_lims.service.audit.AuditLogService;
import it.elismart_lims.service.validation.ValidationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Business logic for MeasurementPair operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementPairService {

    private final MeasurementPairRepository measurementPairRepository;
    private final AuditLogService auditLogService;

    /**
     * Persist a list of measurement pairs.
     *
     * @param pairs the list of measurement pairs to save
     * @return the saved measurement pair entities
     */
    @Transactional
    public List<MeasurementPair> saveAll(List<MeasurementPair> pairs) {
        log.debug("Saving {} measurement pair(s)", pairs.size());
        return measurementPairRepository.saveAll(pairs);
    }

    /**
     * Update the raw signal values of an existing measurement pair and recalculate
     * the derived metrics (signal mean, %CV) server-side.
     *
     * <p>Only the pair's owner experiment ID is validated; pairType is immutable.
     * Every field that actually changes produces an {@link it.elismart_lims.model.AuditLog}
     * entry via {@link AuditLogService}.</p>
     *
     * @param request      the update payload (id, signal1, signal2, concentrationNominal)
     * @param experimentId the ID of the owning experiment, used to validate ownership
     * @throws ResourceNotFoundException if no pair exists with the given ID
     * @throws IllegalArgumentException  if the pair does not belong to the given experiment
     */
    @Transactional
    public void update(MeasurementPairUpdateRequest request, Long experimentId) {
        MeasurementPair pair = measurementPairRepository.findById(request.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MeasurementPair not found with id: " + request.id()));

        if (!pair.getExperiment().getId().equals(experimentId)) {
            throw new IllegalArgumentException(
                    "Measurement pair " + request.id()
                    + " does not belong to experiment " + experimentId);
        }

        auditIfChanged("MeasurementPair", pair.getId(), "signal1",
                pair.getSignal1(), request.signal1());
        auditIfChanged("MeasurementPair", pair.getId(), "signal2",
                pair.getSignal2(), request.signal2());
        if (request.concentrationNominal() != null) {
            auditIfChanged("MeasurementPair", pair.getId(), "concentrationNominal",
                    pair.getConcentrationNominal(), request.concentrationNominal());
        }

        double s1 = request.signal1();
        double s2 = request.signal2();
        double mean = ValidationConstants.calculateSignalMean(s1, s2);
        double cv = ValidationConstants.calculateCvPercent(s1, s2);

        pair.setSignal1(s1);
        pair.setSignal2(s2);
        pair.setSignalMean(mean);
        pair.setCvPct(cv);

        if (request.concentrationNominal() != null) {
            pair.setConcentrationNominal(request.concentrationNominal());
        }

        // Recovery% depends on the calibration curve (not available here); leave it unchanged.

        measurementPairRepository.save(pair);
        log.debug("Updated measurement pair id: {}", pair.getId());
    }

    /**
     * Update the outlier flag of an existing measurement pair.
     *
     * <p>Produces an {@link it.elismart_lims.model.AuditLog} entry when the flag value changes.</p>
     *
     * @param id      the measurement pair ID
     * @param request the outlier update payload
     * @return the updated {@link MeasurementPairResponse}
     * @throws ResourceNotFoundException if no pair exists with the given ID
     */
    @Transactional
    public MeasurementPairResponse updateOutlier(Long id, OutlierUpdateRequest request) {
        MeasurementPair pair = measurementPairRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MeasurementPair not found with id: " + id));

        auditIfChanged("MeasurementPair", id, "isOutlier", pair.getIsOutlier(), request.isOutlier());

        pair.setIsOutlier(request.isOutlier());

        measurementPairRepository.save(pair);
        log.debug("Updated outlier flag for measurement pair id: {} → {}", id, request.isOutlier());
        return MeasurementPairMapper.toResponse(pair);
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
}

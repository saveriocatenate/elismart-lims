package it.elismart_lims.service;

import it.elismart_lims.dto.MeasurementPairUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.repository.MeasurementPairRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for MeasurementPair operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementPairService {

    private final MeasurementPairRepository measurementPairRepository;

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
     * the derived metrics (signal mean, %CV, %Recovery) server-side.
     *
     * <p>Only the pair's owner experiment ID is validated; pairType is immutable.</p>
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

        double s1 = request.signal1();
        double s2 = request.signal2();
        double mean = (s1 + s2) / 2.0;
        double cv = mean != 0 ? (Math.abs(s1 - s2) / mean) * 100.0 : 0.0;

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
}

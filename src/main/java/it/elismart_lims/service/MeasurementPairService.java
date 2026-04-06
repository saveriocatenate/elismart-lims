package it.elismart_lims.service;

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
}

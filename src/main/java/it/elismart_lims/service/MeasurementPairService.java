package it.elismart_lims.service;

import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.repository.MeasurementPairRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for MeasurementPair operations.
 */
@Service
@RequiredArgsConstructor
public class MeasurementPairService {

    private final MeasurementPairRepository measurementPairRepository;

    /**
     * Persist a list of measurement pairs.
     */
    @Transactional
    public List<MeasurementPair> saveAll(List<MeasurementPair> pairs) {
        return measurementPairRepository.saveAll(pairs);
    }
}

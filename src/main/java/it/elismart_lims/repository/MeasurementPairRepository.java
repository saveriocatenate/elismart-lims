package it.elismart_lims.repository;

import it.elismart_lims.model.MeasurementPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for MeasurementPair entities.
 */
public interface MeasurementPairRepository extends JpaRepository<MeasurementPair, Long> {
}
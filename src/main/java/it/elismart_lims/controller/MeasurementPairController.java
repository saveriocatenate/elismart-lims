package it.elismart_lims.controller;

import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.OutlierUpdateRequest;
import it.elismart_lims.service.MeasurementPairService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for MeasurementPair operations.
 *
 * <p>Measurement pairs are created as part of an experiment (POST /api/experiments)
 * and updated via PUT /api/experiments/{id}. This controller exposes targeted
 * PATCH operations for individual pair fields that have dedicated update semantics.</p>
 */
@RestController
@RequestMapping("/api/measurement-pairs")
@RequiredArgsConstructor
public class MeasurementPairController {

    private final MeasurementPairService measurementPairService;

    /**
     * Update the outlier flag of a single measurement pair.
     *
     * @param id      the measurement pair ID
     * @param request the outlier update payload
     * @return 200 with the updated {@link MeasurementPairResponse}, or 404 if not found
     */
    @PatchMapping("/{id}/outlier")
    public ResponseEntity<MeasurementPairResponse> updateOutlier(
            @PathVariable Long id,
            @Valid @RequestBody OutlierUpdateRequest request) {
        return ResponseEntity.ok(measurementPairService.updateOutlier(id, request));
    }
}

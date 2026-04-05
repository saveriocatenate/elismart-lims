package it.elismart_lims.controller;

import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.mapper.MeasurementPairMapper;
import it.elismart_lims.mapper.UsedReagentBatchMapper;
import it.elismart_lims.service.ExperimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Experiment operations.
 */
@RestController
@RequestMapping("/api/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;

    /**
     * Get an experiment by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExperimentResponse> getById(@PathVariable Long id) {
        var response = experimentService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new experiment with mandatory reagent validation.
     */
    @PostMapping
    public ResponseEntity<ExperimentResponse> create(@Valid @RequestBody ExperimentRequest request) {
        var response = experimentService.create(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Delete an experiment by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        experimentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search experiments with optional filters and pagination.
     */
    @PostMapping("/search")
    public ResponseEntity<ExperimentPage> search(@RequestBody ExperimentSearchRequest request) {
        return ResponseEntity.ok(experimentService.search(request));
    }
}

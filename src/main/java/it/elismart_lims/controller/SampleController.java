package it.elismart_lims.controller;

import it.elismart_lims.dto.SampleCreateRequest;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.dto.SampleUpdateRequest;
import it.elismart_lims.service.SampleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for {@link it.elismart_lims.model.Sample} CRUD operations.
 */
@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    /**
     * Return all samples with pagination.
     *
     * @param pageable pagination and sorting parameters (default page size: 20)
     * @return 200 OK with a page of {@link SampleResponse} DTOs
     */
    @GetMapping
    public ResponseEntity<Page<SampleResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sampleService.getAll(pageable));
    }

    /**
     * Return a sample by its primary key.
     *
     * @param id the sample ID
     * @return 200 OK with the {@link SampleResponse}, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<SampleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sampleService.getById(id));
    }

    /**
     * Return a sample by its unique barcode.
     *
     * <p>This endpoint is declared before {@code /{id}} so that the literal path
     * segment {@code /barcode} is matched first by Spring MVC.</p>
     *
     * @param barcode the barcode to search for
     * @return 200 OK with the {@link SampleResponse}, or 404 if not found
     */
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<SampleResponse> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(sampleService.getByBarcode(barcode));
    }

    /**
     * Create a new sample.
     *
     * @param request the validated creation payload
     * @return 201 Created with the {@link SampleResponse}
     */
    @PostMapping
    public ResponseEntity<SampleResponse> create(@Valid @RequestBody SampleCreateRequest request) {
        return new ResponseEntity<>(sampleService.create(request), HttpStatus.CREATED);
    }

    /**
     * Update mutable fields of an existing sample.
     *
     * @param id      the sample ID
     * @param request the update payload
     * @return 200 OK with the updated {@link SampleResponse}, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<SampleResponse> update(
            @PathVariable Long id,
            @RequestBody SampleUpdateRequest request) {
        return ResponseEntity.ok(sampleService.update(id, request));
    }
}

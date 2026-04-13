package it.elismart_lims.controller;

import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.dto.ReagentBatchResponse;
import it.elismart_lims.service.ReagentBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for {@link it.elismart_lims.model.ReagentBatch} CRUD operations.
 *
 * <p>Base path: {@code /api/reagent-batches}</p>
 */
@RestController
@RequestMapping("/api/reagent-batches")
@RequiredArgsConstructor
public class ReagentBatchController {

    private final ReagentBatchService reagentBatchService;

    /**
     * Register a new reagent batch.
     *
     * @param request the validated create payload
     * @return 201 Created with the {@link ReagentBatchResponse}
     */
    @PostMapping
    public ResponseEntity<ReagentBatchResponse> create(
            @Valid @RequestBody ReagentBatchCreateRequest request) {
        return new ResponseEntity<>(reagentBatchService.create(request), HttpStatus.CREATED);
    }

    /**
     * List all batches for a given reagent, or retrieve a single batch by ID.
     *
     * <p>When {@code reagentId} is provided, returns all batches for that reagent.
     * When absent, returns an empty list (use {@code GET /{id}} for single-batch lookup).</p>
     *
     * @param reagentId optional reagent catalog ID filter
     * @return 200 OK with the list of matching {@link ReagentBatchResponse} DTOs
     */
    @GetMapping
    public ResponseEntity<List<ReagentBatchResponse>> listByReagent(
            @RequestParam(required = false) Long reagentId) {
        if (reagentId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(reagentBatchService.findByReagentId(reagentId));
    }

    /**
     * Get a single reagent batch by its primary key.
     *
     * @param id the batch ID
     * @return 200 OK with the {@link ReagentBatchResponse}, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReagentBatchResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reagentBatchService.findById(id));
    }
}

package it.elismart_lims.controller;

import it.elismart_lims.dto.ReagentCatalogRequest;
import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.mapper.ReagentCatalogMapper;
import it.elismart_lims.service.ReagentCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for ReagentCatalog CRUD operations.
 */
@RestController
@RequestMapping("/api/reagent-catalogs")
@RequiredArgsConstructor
public class ReagentCatalogController {

    private final ReagentCatalogService reagentCatalogService;

    /**
     * Get all reagent catalogs with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<ReagentCatalogResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reagentCatalogService.getAll(pageable));
    }

    /**
     * Get a reagent catalog by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReagentCatalogResponse> getById(@PathVariable Long id) {
        var entity = reagentCatalogService.getById(id);
        return ResponseEntity.ok(entity);
    }

    /**
     * Create a new reagent catalog.
     */
    @PostMapping
    public ResponseEntity<ReagentCatalogResponse> create(
            @Valid @RequestBody ReagentCatalogRequest request) {
        var entity = reagentCatalogService.create(ReagentCatalogMapper.toEntity(request));
        return new ResponseEntity<>(ReagentCatalogMapper.toResponse(entity), HttpStatus.CREATED);
    }

    /**
     * Delete a reagent catalog by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reagentCatalogService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

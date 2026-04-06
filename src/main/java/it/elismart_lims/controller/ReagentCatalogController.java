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
     *
     * @param pageable pagination and sorting parameters (default page size: 20)
     * @return 200 OK with a paginated list of ReagentCatalogResponse DTOs
     */
    @GetMapping
    public ResponseEntity<Page<ReagentCatalogResponse>> getAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reagentCatalogService.getAll(pageable));
    }

    /**
     * Get a reagent catalog by ID.
     *
     * @param id the reagent catalog ID
     * @return 200 OK with the ReagentCatalogResponse, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReagentCatalogResponse> getById(@PathVariable Long id) {
        var entity = reagentCatalogService.getById(id);
        return ResponseEntity.ok(entity);
    }

    /**
     * Create a new reagent catalog entry.
     *
     * @param request the validated reagent creation payload
     * @return 201 Created with the ReagentCatalogResponse
     */
    @PostMapping
    public ResponseEntity<ReagentCatalogResponse> create(
            @Valid @RequestBody ReagentCatalogRequest request) {
        var entity = reagentCatalogService.create(ReagentCatalogMapper.toEntity(request));
        return new ResponseEntity<>(ReagentCatalogMapper.toResponse(entity), HttpStatus.CREATED);
    }

    /**
     * Delete a reagent catalog entry by ID.
     *
     * @param id the reagent catalog ID
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reagentCatalogService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

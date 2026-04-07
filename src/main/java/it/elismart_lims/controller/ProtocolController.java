package it.elismart_lims.controller;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.mapper.ProtocolMapper;
import it.elismart_lims.service.ProtocolService;
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
 * REST controller for Protocol CRUD operations.
 */
@RestController
@RequestMapping("/api/protocols")
@RequiredArgsConstructor
public class ProtocolController {

    private final ProtocolService protocolService;

    /**
     * Get all protocols.
     *
     * @return list of all ProtocolResponse DTOs
     */
    @GetMapping
    public ResponseEntity<List<ProtocolResponse>> getAll() {
        return ResponseEntity.ok(protocolService.getAll());
    }

    /**
     * Search protocols by partial name match (case-insensitive) with pagination.
     * Returns all protocols when the {@code name} parameter is absent or blank.
     *
     * <p>This endpoint is declared before {@code /{id}} so that the literal path
     * segment {@code /search} is matched first by Spring MVC.</p>
     *
     * @param name     optional partial name filter
     * @param pageable pagination and sorting parameters (default page size: 20)
     * @return a paginated list of matching ProtocolResponse DTOs
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ProtocolResponse>> search(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(protocolService.search(name, pageable));
    }

    /**
     * Update an existing protocol.
     *
     * <p>Blocked if experiments are linked to this protocol — remove them first.</p>
     *
     * @param id      the protocol ID
     * @param request the validated update payload
     * @return 200 OK with the updated ProtocolResponse, 404 if not found, 409 if experiments exist
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProtocolResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProtocolRequest request) {
        return ResponseEntity.ok(protocolService.update(id, request));
    }

    /**
     * Get a protocol by ID.
     *
     * @param id the protocol ID
     * @return the found ProtocolResponse, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProtocolResponse> getById(@PathVariable Long id) {
        var response = protocolService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new protocol.
     *
     * @param request the validated protocol creation payload
     * @return the created ProtocolResponse with HTTP 201
     */
    @PostMapping
    public ResponseEntity<ProtocolResponse> create(@Valid @RequestBody ProtocolRequest request) {
        var protocol = protocolService.create(ProtocolMapper.toEntity(request));
        return new ResponseEntity<>(protocol, HttpStatus.CREATED);
    }

    /**
     * Delete a protocol by ID.
     *
     * @param id the protocol ID
     * @return HTTP 204 on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        protocolService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

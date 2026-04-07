package it.elismart_lims.controller;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.mapper.ProtocolMapper;
import it.elismart_lims.service.ProtocolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
     * Search protocols by partial name match (case-insensitive).
     * Returns all protocols when the {@code name} parameter is absent or blank.
     *
     * <p>This endpoint is declared before {@code /{id}} so that the literal path
     * segment {@code /search} is matched first by Spring MVC.</p>
     *
     * @param name optional partial name filter
     * @return list of matching ProtocolResponse DTOs
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProtocolResponse>> search(
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(protocolService.search(name));
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

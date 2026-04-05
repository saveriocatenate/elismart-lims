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

/**
 * REST controller for Protocol CRUD operations.
 */
@RestController
@RequestMapping("/api/protocols")
@RequiredArgsConstructor
public class ProtocolController {

    private final ProtocolService protocolService;

    /**
     * Get a protocol by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProtocolResponse> getById(@PathVariable Long id) {
        var response = protocolService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new protocol.
     */
    @PostMapping
    public ResponseEntity<ProtocolResponse> create(@Valid @RequestBody ProtocolRequest request) {
        var protocol = protocolService.create(ProtocolMapper.toEntity(request));
        return new ResponseEntity<>(protocol, HttpStatus.CREATED);
    }

    /**
     * Delete a protocol by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        protocolService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

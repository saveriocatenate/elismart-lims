package it.elismart_lims.controller;

import it.elismart_lims.dto.ProtocolReagentSpecRequest;
import it.elismart_lims.dto.ProtocolReagentSpecResponse;
import it.elismart_lims.service.ProtocolReagentSpecService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for ProtocolReagentSpec CRUD operations.
 */
@RestController
@RequestMapping("/api/protocol-reagent-specs")
@RequiredArgsConstructor
public class ProtocolReagentSpecController {

    private final ProtocolReagentSpecService protocolReagentSpecService;

    /**
     * Get all reagent specifications for a given protocol.
     *
     * @param protocolId the protocol ID
     * @return list of ProtocolReagentSpecResponse DTOs
     */
    @GetMapping
    public ResponseEntity<List<ProtocolReagentSpecResponse>> getByProtocol(
            @RequestParam Long protocolId) {
        return ResponseEntity.ok(protocolReagentSpecService.getByProtocolId(protocolId));
    }

    /**
     * Create a new protocol-reagent specification.
     *
     * @param request the creation request
     * @return the created ProtocolReagentSpecResponse with HTTP 201
     */
    @PostMapping
    public ResponseEntity<ProtocolReagentSpecResponse> create(
            @Valid @RequestBody ProtocolReagentSpecRequest request) {
        return new ResponseEntity<>(protocolReagentSpecService.create(request), HttpStatus.CREATED);
    }
}

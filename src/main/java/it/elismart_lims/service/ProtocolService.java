package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ProtocolMapper;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.repository.ProtocolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for Protocol operations.
 */
@Service
@RequiredArgsConstructor
public class ProtocolService {

    private final ProtocolRepository protocolRepository;

    /**
     * Find a protocol by its ID.
     */
    @Transactional(readOnly = true)
    public ProtocolResponse getById(Long id) {
        var protocol = getEntityById(id);
        return ProtocolMapper.toResponse(protocol);
    }

    public Protocol getEntityById(Long id) {
        return protocolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Protocol not found with id: " + id));
    }

    /**
     * Create a new protocol.
     */
    @Transactional
    public ProtocolResponse create(Protocol protocol) {
        return ProtocolMapper.toResponse(protocolRepository.save(protocol));
    }

    /**
     * Delete a protocol by its ID.
     */
    @Transactional
    public void delete(Long id) {
        if (!protocolRepository.existsById(id)) {
            throw new ResourceNotFoundException("Protocol not found with id: " + id);
        }
        protocolRepository.deleteById(id);
    }
}
